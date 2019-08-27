/*
 * TrackMeARouteInterface.cpp
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
 *  Created on: Mar 9, 2016
 *      Author: Michael P Camilleri
 */
#include <iostream>

#include <MUL++/include/Utilities.h>
#include <MCF++/include/Logger.h>
#include <MCF++/include/Socket.h>

#include "include/ConnectionManager.h"
#include "include/DatabaseManager.h"

LOG_FOLDER(InterfaceLog);

int main()
{
	zmq::context_t context(1);
	CZMQSocket control_socket(context, ZMQ_PAIR, CConnectionManager::MANAGER_CONTROL, false);
	control_socket.Connect();	//Connect to control socket TODO handle exceptions
	CZMQSocket dbase_socket(context, ZMQ_PAIR, CDatabaseManager::DATABASE_CONTROL, false);
	dbase_socket.Connect();	//Connect to database socket

	bool end = false;
	std::cout << "Starting Interface:..." << std::endl;
	while (!end)
	{
		//Variables
		CMessage 	msg;
		std::string 	user_in;
		std::string		tmp_str;
		size_t			tmp_idx;
/*		char * 			next_ptr;*/

		//Prompt and accept input
		std::cout << "USER_COMMAND > _ " << std::flush;

		//First read in the whole line
		std::getline(std::cin, user_in);
		user_in = trim(user_in);

		//Now attempt to parse message:
		tmp_idx = user_in.find_first_of(' ');
		tmp_str = user_in.substr(0, tmp_idx);

		if (tmp_str.compare("exit") == 0)
		{
			std::cout << "Terminating Interface: (to terminate server, use command 'kill')" << std::endl;
			end = true;
		}
		else if (tmp_str.compare("kill") == 0)
		{
			std::cout << "Sending Term Message to DBASE" << std::endl;
			dbase_socket.SendMsg(CMessage::TerminateMsg());

			std::cout << "Waiting for Reply from Database" << std::endl;
			dbase_socket.ReadMsg(true);

			std::cout << "Sending Term Message to server" << std::endl;
			control_socket.SendMsg(CMessage::TerminateMsg());

			std::cout << "Waiting for Reply from Server" << std::endl;
			control_socket.ReadMsg(true);
			end = true;
		}
		else if (tmp_str.compare("dbase") == 0)
		{
			//Ignore - so far the databse does not accept commands from the user...
		}
		else if (tmp_str.compare("server") == 0)
		{
			//Ignore - so far the server does not accept commands from the user...
		}
		else
		{
			std::cout << "\n Invalid Command: Useage is {kill} \n"
						 "                           or {exit} \n"
						 "                           or {recipient} {Command} {Data}" << std::endl;
		}
	}
}

