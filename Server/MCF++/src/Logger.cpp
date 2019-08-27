/*
 * Logger.cpp
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
 *  Created on: May 5, 2016
 *      Author: drt_researcher
 */

#include <sys/stat.h>
#include <sys/time.h>

#include <MUL++/include/Utilities.h>
#include <MUL++/include/zhelpers.hpp>

#include "../include/Logger.h"
#include "../include/Message.h"

constexpr const char* const CLogger::LEVEL_NAMES[];

CLogger & CLogger::_this()
{
	static CLogger inst;
	return inst;
}

CLogger::CLogger() :
	mContext(1)	//TODO consider removing
{
	mSocketPtr = NULL;
	//mLastSave  = getTime().tv_sec;
	pthread_mutex_init(&mLock, NULL);
	mTerminate = false;

	//Resolve Output File
	InitialiseOutputFile();
	if (pthread_create(&mRefresh, NULL, Refresh, this) != 0) { std::cout << "Logger Refresh Error" << std::endl; mRefresh = 0; }
}

CLogger::~CLogger()
{
	//Wait first for thread to join...
	if (mRefresh > 0)
	{
		mTerminate = true;
		pthread_join(mRefresh, nullptr);
	}
	mPersist << "============================================================================\n";
	mPersist.close();	//Close file stream
	if (mSocketPtr != NULL)
	{
		s_send(*mSocketPtr, CMessage::TerminateMsg()); //Send Terminate Message
		mSocketPtr->close();
		delete(mSocketPtr);
	}
	pthread_mutex_destroy(&mLock);
}

void CLogger::EnablePublishing()
{
	pthread_mutex_lock(&(_this().mLock));
	if (_this().mSocketPtr == NULL)
	{
		_this().mSocketPtr = new zmq::socket_t(_this().mContext, ZMQ_PUB);
		try { _this().mSocketPtr->bind(DEBUG_TOPIC_STUB + DEBUG_FOLDER + ".ipc"); }
		catch (zmq::error_t const & e)
		{
			_this().Log_(getTime(), 4, "Logger", std::string("Unable to Setup Publishing due to Socket Error: ").append(e.what()));
			delete(_this().mSocketPtr);
			_this().mSocketPtr = NULL;
		}
	}
	pthread_mutex_unlock(&(_this().mLock));
}

void CLogger::DisablePublishing()
{
	pthread_mutex_lock(&(_this().mLock));
	if (_this().mSocketPtr != NULL)
	{
		s_send(*(_this().mSocketPtr), CMessage::TerminateMsg());
		_this().mSocketPtr->close();
		delete(_this().mSocketPtr);
		_this().mSocketPtr = NULL;
	}
	pthread_mutex_unlock(&(_this().mLock));
}

void CLogger::Debug(std::string const & tag, std::string const & msg)
{
	_this().LockLog(getTime(), 1, tag, msg);
}

void CLogger::Info(std::string const & tag, std::string const & msg)
{
	_this().LockLog(getTime(), 2, tag, msg);
}

void CLogger::Warn(std::string const & tag, std::string const & msg)
{
	_this().LockLog(getTime(), 3, tag, msg);
}

void CLogger::Error(std::string const & tag, std::string const & msg)
{
	_this().LockLog(getTime(), 4, tag, msg);
}

void * CLogger::Refresh(void * this_ptr)
{
	CLogger * _this_ptr = static_cast<CLogger *>(this_ptr);
	uint refresh_rate = 0;
	if (_this_ptr != nullptr)
	{
		while (!_this_ptr->mTerminate)
		{
			if (refresh_rate++ == SAVE_RATE)
			{
				pthread_mutex_lock(&(_this_ptr->mLock));
				_this_ptr->ResolveOutputFile();
				_this_ptr->mPersist.flush();
				pthread_mutex_unlock(&(_this_ptr->mLock));
				refresh_rate = 0;
			}
			usleep(1000000);	//Sleep for 1 second (1000 micro-seconds)
		}
	}
	else
	{
		std::cout << "Error in Casting _this pointer" << std::endl;
	}
	return nullptr;
}

void CLogger::Log_(timeval const & call_time, const int level, std::string const & tag, std::string const & msg)
{
	//Resolve the output file to write to...
	try
	{
		ResolveOutputFile();

		//Extract Time Format
		char buffer[30]; strftime(buffer, 30, "%d/%m/%Y %H:%M:%S", localtime(&call_time.tv_sec));
		std::string log_msg = "["; log_msg.append(buffer).append(".").append(to_String(call_time.tv_usec)).append("] {").append(LEVEL_NAMES[level]).append("} ").append(tag).append(" : ").append(msg);

		//Write to output stream
		mPersist << log_msg << "\n" << std::flush; //TODO remove flushing!

		//If need be publish for subscribers to get it...
		if (mSocketPtr != NULL)
		{
			CMessage msg;
			msg.mCode = CMessage::CM_LOG_VERBOSE + level;
			msg.mData = log_msg;	//Prepend the level
			s_send(*mSocketPtr, msg.serialize());
		}
	}
	catch(std::exception & e)
	{
		std::cout << "Caught Unhandled Exception - " << e.what() << std::endl;
	}

}

void CLogger::InitialiseOutputFile()
{
	time_t curr_time = time(NULL);
	tm     time_now  = * localtime(&curr_time);

	//First Create Parent Folder:
	if ((mkdir(DEBUG_FOLDER.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { throw; }

	//Now Create Monthly Separator
	char folder[10]; strftime(folder, 10, DEBUG_SEP_STUB, &time_now);
	if ((mkdir((std::string(DEBUG_FOLDER).append("/").append(folder)).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { throw; }

	//Open File (try append mode)
	char file[10]; strftime(file, 10, DEBUG_FILE_STUB, &time_now);
	mPersist.open((std::string(DEBUG_FOLDER).append("/").append(folder).append("/").append(file)).c_str(), std::ios::app);
	if (!mPersist.is_open()) { throw; }

	//Initialise Midnight Pointer
	curr_time += 24*60*60; 					//This will definitely point to the next day
	time_now = * localtime(&curr_time);
	time_now.tm_sec = 0;
	time_now.tm_min = 0;
	time_now.tm_hour = 0;
	mMidnight = mktime(&time_now);
}

void CLogger::ResolveOutputFile()
{
	time_t curr_time = time(NULL);

	//Only proceed if midnight reached
	if (curr_time >= mMidnight)
	{
		tm time_now = * localtime(&curr_time);
		//First Close the old file if any
		if (mPersist.is_open()) { mPersist.close(); }

		//Now open new one - first create folder if need be
		char folder[10]; strftime(folder, 10, DEBUG_SEP_STUB, &time_now);
		if ((mkdir((std::string(DEBUG_FOLDER).append("/").append(folder)).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { throw std::ios_base::failure("Error!"); } //TODO need to resolve this

		//Open new file (actually truncate to cater for initialisation case)
		char file[10]; strftime(file, 10, DEBUG_FILE_STUB, &time_now);
		mPersist.open((std::string(DEBUG_FOLDER).append("/").append(folder).append("/").append(file)).c_str(), std::ios::trunc);
		if (!mPersist.is_open()) { throw std::ios_base::failure("Error!"); }

		//Finally update Midnight calculation
		mMidnight += 24*60*60;
	}
}

timeval CLogger::getTime()
{
	timeval curTime;
	gettimeofday(&curTime, NULL);
	return curTime;
}
