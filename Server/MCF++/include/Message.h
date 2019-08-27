/*
 * ControlMessage.h
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
 *  Created on: Mar 30, 2016
 *      Author: michael
 */

#ifndef INCLUDE_MESSAGE_H_
#define INCLUDE_MESSAGE_H_

#include <string>
#include <cstdint>

class CMessage
{
public:
	////=========================== Message Definitions ===========================////
	///------------------------- Global Messages (000-099) -------------------------///
	static constexpr uint32_t CM_EMPTY      		=   0;	//!< Empty Message

	static constexpr uint32_t CM_PING_REQ			=  11;	//!< Request ping
	static constexpr uint32_t CM_PING_REP   		=  12;	//!< Reply to ping

	static constexpr uint32_t CM_OK					=  21;	//!< General OK Message
	static constexpr uint32_t CM_INV				=  22;	//!< Invalid request
	static constexpr uint32_t CM_FAIL				=  23;	//!< General Failure Message
	static constexpr uint32_t CM_TERM				=  24;	//!< Termination Message

	static constexpr uint32_t CM_LOG_VERBOSE		=  31;	//!< Log/Verbose Message (unused)
	static constexpr uint32_t CM_LOG_DEBUG			=  32;	//!< Log/Debug Message
	static constexpr uint32_t CM_LOG_INFO			=  33;	//!< Log/Inform Message
	static constexpr uint32_t CM_LOG_WARN			=  34;	//!< Log/Warning Message
	static constexpr uint32_t CM_LOG_ERROR			=  35;	//!< Log/Error Message

	static constexpr uint32_t CM_TIME_INIT    		= 51; 	//!< Time Synchronisation Initialisation Message: (Message contains current time (us), desired time (us) and SpeedUp (double))
	static constexpr uint32_t CM_TIME_SYNC			= 52;	//!< Time re-Synchronisation Message: (Message contains the current time (us) and the desired time (us))

	///// Member Fields ////
	uint32_t 		mCode;	//The code to use
	std::string		mData;	//The Data part

	explicit CMessage();
	explicit CMessage(uint32_t code, std::string const & data);

	//// Static Functions ////
	static std::string OKMsg(std::string const & param);
	static std::string OKMsg();
	static std::string FailureMsg(std::string const & reason);
	static std::string FailureMsg();
	static std::string TerminateMsg();
	static std::string PingReqMsg(std::string const & msg);
	static std::string PingReqMsg();
	static std::string PingRepMsg(std::string const & msg);
	static std::string PingRepMsg();
	static std::string InvalidMsg();

	///// Member Functions /////
	/**
	 * \brief  Converts the message to a string for writing to output stream
	 * \detail The function does not modify the underlying message in any way
	 */
	std::string serialize() const;

	/**
	 * \brief  Converts the input string to a message and returns it for potential chaining.
	 * \detail The msg is expected to contain both the message code as well as the message data, separated by a space.
	 */
	CMessage & serialize(std::string const & msg);

	/**
	 * \brief  Converts the input string stream to a message and returns it for potential chaining.
	 * \detail The msg is expected to contain both the message code as well as the message data, separated by a space.
	 */
	CMessage & serialize(std::istringstream & msg);
};


#endif /* INCLUDE_MESSAGE_H_ */
