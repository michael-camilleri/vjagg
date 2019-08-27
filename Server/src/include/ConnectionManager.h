/*
 * ConnectionManager.h
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

#ifndef INCLUDE_CONNECTIONMANAGER_H_
#define INCLUDE_CONNECTIONMANAGER_H_


#include <pthread.h>
#include <string>
#include <map>

#include <MCF++/include/Message.h>
#include <MCF++/include/Socket.h>

#include "Debug.h"
#include "DatabaseManager.h"


/**
 * \brief  Active Connection Manager Structure
 * \detail This structure incorporates the information regarding an active connection between a client and our server.
 */
struct ConnectionType_t
{
	std::string 	mClientIP;		//!< The client IP which is on an active connection
	CZMQSocket *	mThreadSocket;	//!< The connection Socket (PAIR type) we are using to communicate with the child thread (pointer: must be instantiated and handled correctly)
	pthread_t		mWorkerThread;	//!< The Worker Thread on which we are servicing the client
};

struct WorkerType_t
{
	zmq::context_t * mContext;		//!< The context to use (as a pointer)
	int			     mSocket;		//!< The open socket in use: must be cleaned by the user...
	std::string      mTopic;		//!< Topic over which to communicate
};

class CConnectionManager
{
	//==================== Static Constants =========================//
public:
	static constexpr auto MANAGER_CONTROL 	  = "ipc:///tmp/ManagerControl.ipc";	//!< Topic for communication with text-base controller...
	static constexpr auto WORKER_CONTROL  	  = "inproc://worker_";				    //!< Topic for communication with child threads

	static constexpr uint32_t LISTENER_PORT   = 1500;								//!< Listener port

	///-------------------------- TMAR Messages (100-199) --------------------------///
public:
	static constexpr uint32_t TMAR_IDENT_REQ			= 101;	//!< Identification request
	static constexpr uint32_t TMAR_IDENT_REP  			= 102;	//!< Identification reply
	static constexpr uint32_t TMAR_IDENT_JOUR_VER  		= 103;	//!< Verify Identification Code (typically between server and dbase) [dbase will reply with OK or FAIL message]
	static constexpr uint32_t TMAR_IDENT_VER			= 104;	//!< Verify Identification Code (no journey)

	static constexpr uint32_t TMAR_REQ_SEND_JOURNEY   	= 111;  //Client Request to send a journey: Data will contain the user identifier, & journey unique identifier
	static constexpr uint32_t TMAR_OK_TO_SEND         	= 112;  //Server Send Confirmation that can send data
	static constexpr uint32_t TMAR_SENDING_HEADER     	= 113;  //Client Sending Journey Header [Length, Termination Code, Mode, Reason]: Server will reply (if ok) with MSG_OK_TO_SEND again...
	static constexpr uint32_t TMAR_SENDING_ROUTE_PART 	= 114;  //Client Sending the Actual Journey Route
	static constexpr uint32_t TMAR_ROUTE_PART_RECVD		= 115;	//Route Part Received
	static constexpr uint32_t TMAR_SENDING_ROUTE_DONE	= 116;	//Entire route received (data would normally be empty)
	static constexpr uint32_t TMAR_JOURNEY_RECEIVED   	= 117;  //Reply from server that journey received...
	static constexpr uint32_t TMAR_STORE_JOURNEY		= 118;	//Command to store Journey (server to dbase) [dbase will reply with journey received]

	static constexpr uint32_t TMAR_REQ_SEND_LOGDATA		= 121;	//Request to send Log Data
	static constexpr uint32_t TMAR_OK_TO_LOG          	= 122;  //Ok to send log data
	static constexpr uint32_t TMAR_SENDING_LOG_PART   	= 123;  //Sending part of a log
	static constexpr uint32_t TMAR_LOG_PART_RECVD     	= 124;  //Received Log part
	static constexpr uint32_t TMAR_SENDING_LOG_DONE   	= 125;  //Last part of log information
	static constexpr uint32_t TMAR_LOG_RECEIVED       	= 126;  //Received Log
	static constexpr uint32_t TMAR_STORE_LOG			= 127;	//Store the received log file...

private:
	static constexpr uint32_t LOOP_RATE			 = 1000;	//!< 1ms loop rate
	static constexpr uint32_t FAST_RATE			 = 10;		//!< 10us fast sleep
	static constexpr uint32_t MAX_FAST_LOOP		 = 1000;	//!< How many cycles to operate at max-fast loop
	static constexpr uint32_t MAX_ACTIVE_CONNX	 = 50;		//!< Maximum number of active connections
	static constexpr uint32_t MAX_PENDING_CONNX  = 10;		//!< Maximum number of connections pending acceptance...
	static constexpr uint32_t CONNX_TIMEOUT	   	 = 10000;	//!< Connection Timeout (in milliseconds)

	static constexpr auto TAG = "CM";//!< Logging Tab

	//================ Initialization Functions ====================//
public:
	/**
	 * Public Constructor
	 * @param context Reference to the zmq context being used...
	 */
	CConnectionManager(zmq::context_t & context);

	/**
	 * Destructor
	 */
	~CConnectionManager();

	/**
	 * \brief  Initialisation Function
	 * \detail Binds the Main Command Socket and starts the Main Service Thread...
	 * @return result of pthread_create()
	 */
	bool Init();


	//================= Thread Functions =================//
public:
	/**
	 * \brief Waits until Service thread is joined...
	 */
	void WaitForCompletion();

protected:
	/**
	 * \brief Run the Connection Manager
	 * \detail Main thread over which the connection manager runs. It handles servicing of requests, socket acceptance etc...
	 */
	static void * doService(void * context_ptr);

	/**
	 * The Worker Thread loop: the argument is the worker type (the child is responsible for deallocating the memory once it is ready)
	 */
	static void * doConnection(void * workertype_ptr);

	//================= Helper Functions =================//
private:
	/**
	 * \brief Terminate all child threads : Not Thread-safe
	 */
	static void TerminateChildren(std::map<uint32_t, ConnectionType_t> & workers);

	/**
	 * \brief Generate a unique identifier for use in the map structure...
	 */
	static uint32_t GenerateUniqueID(std::map<uint32_t, ConnectionType_t> & workers);

	//==================== Member Variables ==================//
protected:
	zmq::context_t &	mContext;		//!< Reference to the underlying context - may need to check if this is valid
	pthread_t			mControlThread; //!< The thread for servicing requests (on which doService runs)
};

#endif /* INCLUDE_CONNECTIONMANAGER_H_ */
