/**
 * ControlMessage.cpp
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

#include "../include/Message.h"

#include <MUL++/include/Utilities.h>

CMessage::CMessage()
{
	mCode = CM_EMPTY;
	mData.clear();
}

CMessage::CMessage(uint32_t code, std::string const & data)
{
	mCode = code;
	mData = data;
}

std::string CMessage::serialize() const
{
	if (mData.size())
	{
		return to_String(mCode, uint8_t(3)).append(" ").append(mData);
	}
	else
	{
		return to_String(mCode, uint8_t(3));
	}
}

CMessage & CMessage::serialize(std::string const & msg)
{
	//Create stream
	std::istringstream tmp_strm(msg);
	std::string tmp_line;

	//Read in the code
	tmp_strm >> mCode;

	//Read in message data, a line at a time..
	mData = "";
	while (tmp_strm.good())
	{
		std::getline(tmp_strm, tmp_line);	//Read entire line, possibly separated by spaces
		mData.append(tmp_line);
	}

	//Clean up
	mData = trim(mData);
	return *this;
}

CMessage & CMessage::serialize(std::istringstream & msg)
{
	std::string tmp_line;

	//Read in the code
	msg >> mCode;

	//Read in message data, a line at a time..
	mData = "";
	while (msg.good())
	{
		std::getline(msg, tmp_line);	//Read entire line, possibly separated by spaces
		mData.append(tmp_line);
	}

	//Clean up
	mData = trim(mData);
	return *this;
}

std::string CMessage::FailureMsg(std::string const & reason)
{
	return to_String(CMessage::CM_FAIL, uint8_t(3)) + " " + reason;
}

std::string CMessage::FailureMsg()
{
	return to_String(CMessage::CM_FAIL, uint8_t(3));
}

std::string CMessage::TerminateMsg()
{
	return to_String(CMessage::CM_TERM, uint8_t(3));
}

std::string CMessage::OKMsg(std::string const & param)
{
	return to_String(CMessage::CM_OK, uint8_t(3)) + " " + param;
}

std::string CMessage::OKMsg()
{
	return to_String(CMessage::CM_OK, uint8_t(3));
}

std::string CMessage::PingReqMsg(std::string const & msg)
{
	return to_String(CMessage::CM_PING_REQ, uint8_t(3)) + " " + msg;
}

std::string CMessage::PingReqMsg()
{
	return to_String(CMessage::CM_PING_REQ, uint8_t(3));
}

std::string CMessage::PingRepMsg(std::string const & msg)
{
	return to_String(CMessage::CM_PING_REP, uint8_t(3)) + " " + msg;
}

std::string CMessage::PingRepMsg()
{
	return to_String(CMessage::CM_PING_REP, uint8_t(3));
}

std::string CMessage::InvalidMsg()
{
	return to_String(CMessage::CM_INV, uint8_t(3));
}

