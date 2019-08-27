//============================================================================
// Name        : TrackMeARouteServer.cpp
// Author      : Michael Camilleri
// Version     :
// Copyright (C) 2019  Michael Camilleri
//		 This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
//		 License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
//		 version.
//		 This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
//		 warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//		 You should have received a copy of the GNU General Public License along with this program. If not, see
//		 http://www.gnu.org/licenses/.
//============================================================================

#include <iostream>

#include <openssl/ssl.h>
#include <openssl/err.h>

#define LOG_LOGGER

#include <MUL++/include/Utilities.h>
#include <MCF++/include/Socket.h>
#include <MCF++/include/Logger.h>

#include "include/ConnectionManager.h"
#include "include/DatabaseManager.h"

LOG_FOLDER(ServerLog)

int main(int argc, char *argv[])
{
	std::cout << "Initialising System..." << std::endl;

	//Parse Command Line Arguments
	if ((argc > 1) && (strncmp(argv[1], "publish", 7) == 0)) { std::cout << "Enabling Publishing" << std::endl; CLogger::EnablePublishing(); }
	else													 { std::cout << "Publishing Disabled" << std::endl; }

	//Initialise Librar(ies)
	//Core SSL
	SSL_library_init();
	SSL_load_error_strings();

	//OpenSSL
	OpenSSL_add_all_algorithms();
	OpenSSL_enable_multithreading(); //Actually mine...

	//Avoid SigPipe Errors
	if (!Disable_SigPipe()) { std::cout << "Unable to Turn Off Sig-Pipe Errors!" << std::endl; }

	//Create Server Communication Framework
	zmq::context_t context(1);
	CZMQSocket control_socket(context, ZMQ_PAIR, CConnectionManager::MANAGER_CONTROL, false);
	try { control_socket.Connect(); }	//Connect to control socket
	catch(zmq::error_t const & e) { std::cout << "Unable to Start control socket: " << e.what() << std::endl; return -1; }
	CZMQSocket dbase_socket(context, ZMQ_PAIR, CDatabaseManager::DATABASE_CONTROL, false);
	try { dbase_socket.Connect(); }	//Connect to database socket
	catch(zmq::error_t const & e) { std::cout << "Unable to Start database socket: " << e.what() << std::endl; return -1; }

	//Create Server & Database
	CConnectionManager server(context);
	CDatabaseManager   dbase(context);

	//Variables
	CMessage msg;

	//Start Database Manager
	std::cout << "Starting Database..." << std::endl;
	if (!dbase.Init())
	{
		std::cout << "... Problem starting DataBase Thread: Shutting down!" << std::endl;
		return 0;
	}
	msg = dbase_socket.ReadMsg(true);			 //Block TODO do timeout
	if (msg.mCode != CMessage::CM_OK)
	{
		std::cout << "... Problem starting DataBase: Shutting down!" << std::endl;
		dbase.WaitForCompletion();
		return 0;
	}

	//Start Server
	std::cout << "Starting Server..." << std::endl;
	if (!server.Init())
	{
		std::cout << "... Problem starting Server Thread: Shutting down!" << std::endl;
		dbase_socket.SendMsg(CMessage::TerminateMsg());
		dbase_socket.ReadMsg(true);	//Wait for reply
		dbase.WaitForCompletion();
		return 0;
	}
	msg = control_socket.ReadMsg(true);		//Block TODO do timeout
	if (msg.mCode != CMessage::CM_OK)
	{
		std::cout << "... Problem starting Server Thread: Shutting down!" << std::endl;
		dbase_socket.SendMsg(CMessage::TerminateMsg());
		dbase_socket.ReadMsg(true);	//Wait for reply
		dbase.WaitForCompletion();
		server.WaitForCompletion();
		return 0;
	}

	control_socket.Close();
	dbase_socket.Close();
	std::cout << "... Success. For interaction, you may now connect the Server Interface." << std::endl;

	dbase.WaitForCompletion();
	server.WaitForCompletion();
	std::cout << "Closing Down." << std::endl;
	return 0;
}
