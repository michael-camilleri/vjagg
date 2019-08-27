/*
 * ConnectionManager.cpp
 *
 *   Copyright (C) 2019  Michael Camilleri
 *
 * 	This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 *	License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * 	version.
 *	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 *	warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 	You should have received a copy of the GNU General Public License along with this program. If not, see
 *	http://www.gnu.org/licenses/.
 *
 *  Created on: Feb 8, 2016
 *      Author: michael
 */

//TODO Revise all dynamic memory allocations (such as the worker_ptr) to make sure that they are being deleted even on
//		premature exit...

#include <unistd.h>

#define LOG_LOGGER

#include "include/ConnectionManager.h"

#include <MUL++/include/Utilities.h>
#include <MCF++/include/Logger.h>

CConnectionManager::CConnectionManager(zmq::context_t & context) :
	mContext(context)
{
	mControlThread = 0;		//Initialise just in case...
	INFO("Initialised.");
}

CConnectionManager::~CConnectionManager()
{
	//Ensure that indeed main thread is closed - this may be unsafe...
	if (mControlThread > 0)
	{
		WARN("Main Servicing Thread was not closed gracefully!");
		pthread_cancel(mControlThread);
	}
}


bool CConnectionManager::Init()
{
	//Ensure that one thread only is ever started...
	if (mControlThread > 0) { WARN("Connection Manager already Started!"); return false; }	//Cannot start an already started service

	//Create Main Loop
	return (pthread_create(&mControlThread, NULL, doService, &mContext) == 0);
}

void CConnectionManager::WaitForCompletion()
{
	DEBUG("Waiting for Completion of Main Thread...");
	if (mControlThread > 0) { pthread_join(mControlThread, NULL); mControlThread = 0; }
	INFO("Main Thread Completed.");
}

void * CConnectionManager::doService(void * context_ptr)
{
	INFO("Entered Service Loop");

	//Get Handle to this class
	zmq::context_t * context = static_cast<zmq::context_t *>(context_ptr);
	bool		     end = false;		//Indicates that end was called
	int				 num_clients = 0;	//Number of clients connected

	//Create Servicing socket and bind to it
	DEBUG("Creating Control Socket");
	CZMQSocket control_socket(*context, ZMQ_PAIR, MANAGER_CONTROL, true);
	try { control_socket.Connect(); }
	catch (zmq::error_t const & e) { ERROR(std::string(e.what()).append(" : Terminating")); return NULL; }

	//Create Listening Socket (with a queue length of 10)
	DEBUG("Setting Up Listener");
	CListener listener(MAX_PENDING_CONNX);
	if (!listener.SetupListener(LISTENER_PORT))
	{
		ERROR("Failed to setup listener port - Closing Down");
		control_socket.SendMsg(CMessage::TerminateMsg()); //Notify control that terminating...
		return NULL;
	}
	else
	{
		DEBUG("Listener Set Up.");
		control_socket.SendMsg(CMessage::OKMsg()); //Notify control that OK
	}

	//Create Map of Child Threads: the key is the actual socket file-descriptor which will be unique!
	std::map<uint32_t, ConnectionType_t> workers;

	INFO("Waiting for Requests...");
	while (!end)
	{
		CMessage 	ctrl;			//Actual control message
		int64_t		client_sckt;	//Placeholder for client socket
		std::string	client_ip;		//Placeholder for the client ip

		//First poll on the main command socket
		ctrl = control_socket.ReadMsg(false);

		switch(ctrl.mCode)
		{
			case CMessage::CM_TERM:
				INFO("Term Msg Recvd.");
				CConnectionManager::TerminateChildren(workers);   //Send Terminate Message to children
				control_socket.SendMsg(CMessage::OKMsg()); //Send OK
				end = true;	//Now end
				break;

			case CMessage::CM_EMPTY:
				//Do nothing
				break;

			default:
				WARN(std::string("Invalid Ctrl Msg: ").append(to_String(ctrl.mCode)));
				control_socket.SendMsg(CMessage::InvalidMsg());
				break;
		}

		if (end) { break; } //Extra precaution: break from loop

		//Now poll on the Listener Socket (only if number of clients is indeed less than the maximum active connections... else listener will queue...
		if (num_clients < MAX_ACTIVE_CONNX)
		{
			if ((client_sckt = listener.CheckForConnection(client_ip)) > CListener::R_IDLE)
			{
				INFO("Connx Req From [" + client_ip + "]");
				uint32_t		 new_ident;	//Unique Identifier for new connection
				ConnectionType_t new_connx;	//Placeholder for new connection
				WorkerType_t *	 new_work;	//Worker Parameters

				//Generate Unique Identifier
				new_ident = GenerateUniqueID(workers);

				//Create Worker Parameters: child thread will be responsible for cleaning this up
				new_work = new WorkerType_t();
				new_work->mContext = context;
				new_work->mSocket  = client_sckt;
				new_work->mTopic   = WORKER_CONTROL + to_String(new_ident);

				//Create Entry
				new_connx.mClientIP		= client_ip;			  			//Copy the client ip for housekeeping
				new_connx.mThreadSocket = new CZMQSocket(*context, ZMQ_PAIR, new_work->mTopic, true);	//Create Socket for inter-process communication
				new_connx.mWorkerThread = 0; 								//For now not known

				//Bind Socket & start thread
				try
				{
					new_connx.mThreadSocket->Connect();
					DEBUG("Connected Client Thread");

					pthread_create(&new_connx.mWorkerThread, NULL, doConnection, new_work); DEBUG("Created New Handler Thread");
					num_clients++;

					//Register Connection type
					workers[new_ident] = new_connx;
				}
				catch (zmq::error_t const & e)
				{
					ERROR(std::string(e.what()).append(" : Child thread not created."));

					//Clean up
					delete(new_work); new_work = nullptr;
					delete(new_connx.mThreadSocket); new_connx.mThreadSocket = nullptr;
				}
			}
		}

		//Now poll on the other sockets
		for (auto it = workers.begin(); it != workers.end(); /*No Increment*/)
		{
			ctrl = it->second.mThreadSocket->ReadMsg(false);
			switch(ctrl.mCode)
			{
				case CMessage::CM_TERM:	//This thread is done and is terminating
					INFO("Terminating Thread servicing [" + it->second.mClientIP + "]");
					(it->second.mThreadSocket)->SendMsg(CMessage::OKMsg()); //Send confirmation
					delete(it->second.mThreadSocket);	it->second.mThreadSocket = nullptr;		//Close the socket for this object and delete it...
					pthread_join(it->second.mWorkerThread, NULL); //Wait for thread to finish
					num_clients--;	//Reduce number of active connections
					it = workers.erase(it);	//Now delete the element from the Map and point to the next element in line
					DEBUG("Thread Terminated Successfully!");
					break;

				case CMessage::CM_EMPTY:
					it++;
					break;

				default:
					WARN("Client Thread sent invalid request [" + to_String(ctrl.mCode) + "]");
					it++;
					break;
			}
		}

		usleep(LOOP_RATE);
	}

	INFO("Server Thread closing down");
	return NULL;	//Return void pointer
}


void * CConnectionManager::doConnection(void * workertype_ptr)
{
	INFO("Spawned Client Loop");

	//Variables
	WorkerType_t * worker_ptr = static_cast<WorkerType_t*>(workertype_ptr);
	bool		   end = false;	//Indicates that end was called
	int  		   waiting = 0;	//Flag so that if waiting for more messages, reduces the sleep cycle
	int			   client_state = CMessage::CM_EMPTY;	//State control for multi-message control
	std::string    journey;
	std::string	   log_data;
	WatchdogTimer  watchdog(CONNX_TIMEOUT);	//Create watchdog with timeout of 10 seconds

	//Socket Connection to Parent
	CZMQSocket parent_connx(*(worker_ptr->mContext), ZMQ_PAIR, worker_ptr->mTopic, false);
	try   { parent_connx.Connect(); }
	catch (zmq::error_t const & e) { ERROR(std::string(e.what()).append(" : Terminating")); delete(worker_ptr); worker_ptr = nullptr; return NULL; }
	DEBUG("Client Thread connected to parent.");
	//TODO need to notify parent that failed!!!!

	//Socket Connection to Database
	CZMQSocket dbase_connx(*(worker_ptr->mContext), ZMQ_REQ, CDatabaseManager::DATABASE_SERVICE, false);
	try { dbase_connx.Connect(); }
	catch (zmq::error_t const & e) { ERROR(std::string(e.what()).append(" : Terminating")); parent_connx.SendMsg(CMessage::TerminateMsg()); parent_connx.ReadMsg(true); delete(worker_ptr); worker_ptr = nullptr; return NULL; }
	DEBUG("Client Thread connected to Database.");

	//Setup Secure Connection
	CSSLSocket  secure_connx("drt_tmar_cert.crt", worker_ptr->mSocket);
	if (secure_connx.Connect())
	{
		DEBUG("Client Thread achieved secure connection!");
		while (!end)
		{
			CMessage ctrl;			//Actual control message

			//Check if there is a message from the parent and branch on it...
			ctrl = parent_connx.ReadMsg(false);
			switch(ctrl.mCode)
			{
				case CMessage::CM_TERM:
					INFO("Client Received Termination Request from Server.");
					secure_connx.SendMsg(ctrl);		//Forward on the message;
					secure_connx.ReadMsg(true);		//Wait on confirmation (block) - TODO I am not yet testing return value!
					parent_connx.SendMsg(CMessage::OKMsg());
					end = true;
					break;

				case CMessage::CM_EMPTY:
					//Do nothing
					break;

				default:
					WARN("Server sent Invalid Request [" + to_String(ctrl.mCode) + "]");
					break;
			}

			if (end) { break; }

			//Check on messages from the client itself
			ctrl = secure_connx.ReadMsg(false);
			switch(ctrl.mCode)
			{
				case CMessage::CM_TERM:	//Can always be sent
					INFO("Client Thread Received Terminate from Client");
					parent_connx.SendMsg(CMessage::TerminateMsg());
					parent_connx.ReadMsg(true); //Wait for reply from parent: TODO not yet reading value
					secure_connx.SendMsg(CMessage::OKMsg()); //Reply that termination received
					end = true;
					break;

				case CMessage::CM_PING_REQ:	//Can always be sent
					INFO("Client Thread Received Ping from Client");
					watchdog.ping();
					secure_connx.SendMsg(CMessage::PingRepMsg());
					waiting = 0;
					break;

				case TMAR_IDENT_REQ:	//Can be sent only if empty
					INFO("Client Thread Received Identity Request from Client");
					watchdog.ping();
					if (client_state == CMessage::CM_EMPTY)
					{
						//Send Request to Database and wait for reply...
						dbase_connx.SendMsg(ctrl); //TODO may need to do asynchronously!!!!
						ctrl = dbase_connx.ReadMsg(true);
					}
					else
					{
						WARN("Client in Invalid state.");
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Idle";
					}
					//Forward Message on to the Client
					secure_connx.SendMsg(ctrl);				//TODO may need to check for validity...
					waiting = 0;
					break;

				case TMAR_REQ_SEND_JOURNEY:
					INFO("Client Thread Received Request to retrieve journey from Client");
					watchdog.ping();
					if (client_state == CMessage::CM_EMPTY)
					{
						//Store the User & Journey ID's (if need be)
						journey = ctrl.mData;
						//Send check to Database
						ctrl.mCode = TMAR_IDENT_JOUR_VER; //Change code and retain data
						dbase_connx.SendMsg(ctrl);
						ctrl = dbase_connx.ReadMsg(true);

						//Verify result
						if (ctrl.mCode == CMessage::CM_OK)
						{
							DEBUG("User Credentials Validated.");
							client_state = TMAR_SENDING_HEADER;	//Expect this next
							waiting = MAX_FAST_LOOP;
							ctrl.mCode = TMAR_OK_TO_SEND;
							ctrl.mData = "";
						}
						else
						{
							WARN("User Credentials Invalid.");
							client_state = CMessage::CM_EMPTY;	//Retain empty state and the message error
							waiting = 0;
							journey = "";								//Clear the UserID
						}
					}
					else
					{
						WARN("Client in Invalid state.");
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						waiting = 0;
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Idle";
					}
					//Forward Message on to the Client
					secure_connx.SendMsg(ctrl);
					break;

				case TMAR_SENDING_HEADER:
					INFO("Client Thread Received Request to retrieve Header from Client");
					watchdog.ping();
					if (client_state == TMAR_SENDING_HEADER)
					{
						DEBUG("Header Registered - " + ctrl.mData);
						journey.append(" "+ctrl.mData);	//Push in the Header Information
						client_state = TMAR_SENDING_ROUTE_PART;
						waiting = MAX_FAST_LOOP;
						ctrl.mCode = TMAR_OK_TO_SEND;
						ctrl.mData = "";
					}
					else
					{
						WARN("Client in Invalid state.");
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						waiting = 0;
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Waiting for Header";
					}
					//Reply to the Client
					secure_connx.SendMsg(ctrl);
					break;

				case TMAR_SENDING_ROUTE_PART:
					INFO("Client Thread Received Route Part from Client");
					watchdog.ping();
					if (client_state == TMAR_SENDING_ROUTE_PART)
					{
						DEBUG("Route Part Registered - " + ctrl.mData);
						journey.append(" "+ctrl.mData);	//Add route Information
						client_state = TMAR_SENDING_ROUTE_PART;
						waiting = MAX_FAST_LOOP;
						ctrl.mCode = TMAR_ROUTE_PART_RECVD;
						ctrl.mData = "";
					}
					else
					{
						WARN("Client in Invalid state.");
						journey = "";
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						waiting = 0;	//Nothing else to wait for...
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Waiting for Route";
					}
					//Reply to the Client
					secure_connx.SendMsg(ctrl);
					break;

				case TMAR_SENDING_ROUTE_DONE:
					INFO("Client Thread Received End of Route from Client");
					watchdog.ping();
					if (client_state == TMAR_SENDING_ROUTE_PART)
					{
						DEBUG("Last Journey Part Registered - " + ctrl.mData);
						journey.append(" "+ctrl.mData);	//Add route Information if any
						ctrl.mCode = TMAR_STORE_JOURNEY;
						ctrl.mData = journey;

						//Send to Database Manager
						DEBUG("Forwarding to Dbase.");
						dbase_connx.SendMsg(ctrl);
						ctrl = dbase_connx.ReadMsg(true);
						watchdog.ping();	//Since after storing to databse, considerable time might have passed
					}
					else
					{
						WARN("Client in Invalid state.");
						journey = "";
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Waiting for Route";
					}
					client_state = CMessage::CM_EMPTY; //In all cases, go back to empty state and retain reply message
					waiting = 0;
					secure_connx.SendMsg(ctrl); //Reply to the Client
					break;

				case TMAR_REQ_SEND_LOGDATA:
					INFO("Client Thread Received Request to send log information from Client");
					watchdog.ping();
					if (client_state == CMessage::CM_EMPTY)
					{
						//Initialize Log File with the User ID & the User Message
						log_data = ctrl.mData;

						//Send check to Database
						ctrl.mCode = TMAR_IDENT_VER; //Change code and retain data
						dbase_connx.SendMsg(ctrl);
						ctrl = dbase_connx.ReadMsg(true);

						//Verify result
						if (ctrl.mCode == CMessage::CM_OK)
						{
							DEBUG("User Credentials Validated.");
							client_state = TMAR_SENDING_LOG_PART;	//Expect this next
							waiting = MAX_FAST_LOOP;
							ctrl.mCode = TMAR_OK_TO_LOG;
							ctrl.mData = "";
						}
						else
						{
							WARN("User Credentials Invalid.");
							client_state = CMessage::CM_EMPTY;	//Retain empty state and the message error
							waiting = 0;
						}
					}
					else
					{
						WARN("Client in Invalid state.");
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						waiting = 0;
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Idle";
					}
					//Forward Message on to the Client
					secure_connx.SendMsg(ctrl);
					break;

				case TMAR_SENDING_LOG_PART:
					INFO("Client Thread Received Log Part");
					watchdog.ping();
					if (client_state == TMAR_SENDING_LOG_PART)
					{
						DEBUG("Log Part Registered.");
						log_data.append(" " +ctrl.mData);	//Add route Information
						client_state = TMAR_SENDING_LOG_PART;
						waiting = MAX_FAST_LOOP;
						ctrl.mCode = TMAR_LOG_PART_RECVD;
						ctrl.mData = "";
					}
					else
					{
						WARN("Client in Invalid state.");
						log_data = "";
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						waiting = 0;
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Waiting for Log Information";
					}
					//Reply to the Client
					secure_connx.SendMsg(ctrl);
					break;

				case TMAR_SENDING_LOG_DONE:
					INFO("Client Thread Received End of Log from Client");
					watchdog.ping();
					if (client_state == TMAR_SENDING_LOG_PART)
					{
						DEBUG("Last Log Part Registered.");
						log_data.append(" "+ctrl.mData);	//Add route Information if any
						ctrl.mCode = TMAR_STORE_LOG;
						ctrl.mData = log_data;
						log_data = "";

						//Send to Database Manager
						DEBUG("Forwarding to Dbase.");
						dbase_connx.SendMsg(ctrl); //TODO do the Database interaction more interactive since client is getting a read timeout error...
						ctrl = dbase_connx.ReadMsg(true);
						watchdog.ping();	//Since after storing to databse, considerable time might have passed

						//In all cases, go back to empty state and retain reply message
						client_state = CMessage::CM_EMPTY;
					}
					else
					{
						WARN("Client in Invalid state.");
						log_data = "";
						client_state = CMessage::CM_EMPTY;	//Go back to empty state
						ctrl.mCode = CMessage::CM_INV;
						ctrl.mData = "Not Waiting for Route";
					}
					//Reply to the Client
					secure_connx.SendMsg(ctrl);
					waiting = 0;
					break;

				case CMessage::CM_EMPTY:
					//Nothing to do - idle
					break;

				default:
					WARN("Client sent Wrong Message [" + to_String(ctrl.mCode) + "]");
					break;
			}

			if (watchdog.expired())	//Check expiring of the watchdog
			{
				WARN("Client Watchdog Expired. Terminating...");
				//Note that this is a silent termination...

				//Notify also the Main Thread that we are shutting down...
				parent_connx.SendMsg(CMessage::TerminateMsg());
				parent_connx.ReadMsg(true);

				//Terminate
				end = true;
			}

			//Finally sleep for a couple milliseconds to avoid stressing the CPU (unless waiting for messages)
			if (waiting < 1) { usleep(LOOP_RATE); }
			else		     { usleep(FAST_RATE); --waiting; }
		}
	}
	else
	{
		ERROR("Client Thread failed to create a secure connection!");
		parent_connx.SendMsg(CMessage::TerminateMsg()); //Now send notice to parent thread that terminating!
		parent_connx.ReadMsg(true);
	}

	//Clean up after use!
	INFO("Client Thread Closing...");
	delete(worker_ptr); worker_ptr = nullptr; //Delete pointer data
	return NULL;
}


void CConnectionManager::TerminateChildren(std::map<uint32_t, ConnectionType_t> & workers)
{
	while (!workers.empty())
	{
		auto it = workers.begin();
		(it->second.mThreadSocket)->SendMsg(CMessage::TerminateMsg());
		(it->second.mThreadSocket)->ReadMsg(true); //Wait until reply received (but do nothing with it)
		delete(it->second.mThreadSocket);	it->second.mThreadSocket = nullptr;   //Delete the pointer and hence call destructor which closes socket
		pthread_join(it->second.mWorkerThread, NULL);							  //Wait for thread to finish
		workers.erase(it);
	}
}


uint32_t CConnectionManager::GenerateUniqueID(std::map<uint32_t, ConnectionType_t> & workers)
{
	//The ID will range from 1 upwards...
	if (workers.empty()) { return 1; }

	//Otherwise, loop (incrementing) until we find a valid free value
	uint32_t tmp_val = 1;
	while(workers.find(tmp_val) != workers.end()) { tmp_val++; }
	return tmp_val;
}
