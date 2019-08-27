/*
 * LogViewer.cpp
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
 *  Created on: Jun 30, 2016
 *      Author: michael
 */

#include <MUL++/include/Utilities.h>
#include <MCF++/include/Socket.h>
#include <MCF++/include/Logger.h>

LOG_FOLDER(MCTLog);

int main(int argc, char *argv[])
{
	zmq::context_t context(1);
	if (argc < 2)
	{
		std::cout << "USAGE MCT++ <log_topic> [log_levels]" << std::endl;
		return 0;
	}

	CZMQSocket subscriber(context, ZMQ_SUB, CLogger::DEBUG_TOPIC_STUB + std::string(argv[1]) + ".ipc", false);
	subscriber.Connect();

	//Now set up the subscriptions
	subscriber.SetOption(ZMQ_SUBSCRIBE, to_String(CMessage::CM_TERM, uint8_t(3)).c_str(), 3); //Subscribe to Termination Message
	if (argc > 1)
	{
		std::string tmp_parser(to_Upper(argv[2]));	//For parsing the string
		if (tmp_parser.find('D') != tmp_parser.npos) { subscriber.SetOption(ZMQ_SUBSCRIBE, to_String(CMessage::CM_LOG_DEBUG, uint8_t(3)).c_str(), 3); } //Subscribe to Termination Message
		if (tmp_parser.find('I') != tmp_parser.npos) { subscriber.SetOption(ZMQ_SUBSCRIBE, to_String(CMessage::CM_LOG_INFO, uint8_t(3)).c_str(), 3); } //Subscribe to Termination Message
		if (tmp_parser.find('W') != tmp_parser.npos) { subscriber.SetOption(ZMQ_SUBSCRIBE, to_String(CMessage::CM_LOG_WARN, uint8_t(3)).c_str(), 3); } //Subscribe to Termination Message
		if (tmp_parser.find('E') != tmp_parser.npos) { subscriber.SetOption(ZMQ_SUBSCRIBE, to_String(CMessage::CM_LOG_ERROR, uint8_t(3)).c_str(), 3); } //Subscribe to Termination Message
	}

	bool end = false;

	std::cout << "Listening to Logs on: " << CLogger::DEBUG_TOPIC_STUB << std::string(argv[1]) << ".ipc" << std::endl;

	while (!end)
	{
		//Variables
		CMessage msg;

		msg = subscriber.ReadMsg(true);	//Read with blocking
		if (msg.mCode == CMessage::CM_TERM) { end = true; std::cout << "Logger is Closing Down" << std::endl; }
		else								{ std::cout << msg.mData << std::endl; }
	}
}


