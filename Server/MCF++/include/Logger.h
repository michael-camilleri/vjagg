/*
 * Logger.h
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

#ifndef INCLUDE_LOGGER_H_
#define INCLUDE_LOGGER_H_

#include <pthread.h>
#include <fstream>
#include <string>


#include <MUL++/include/zmq.hpp>


/**
 * \brief For Encapsulating the logging framework (both to file and to screen)
 */
class CLogger
{
public:
	static const std::string DEBUG_FOLDER;
	static constexpr auto DEBUG_SEP_STUB  = "%Y-%m";
	static constexpr auto DEBUG_FILE_STUB = "%d [%a]";
	static constexpr auto DEBUG_TOPIC_STUB= "ipc:///tmp/";
	static constexpr char const * const LEVEL_NAMES[] = {"Verbose", "Debug", " Info", " Warn", "Error"};

	static constexpr uint64_t SAVE_RATE   = 5;	//Flush every 5 seconds

private:
	/**
	 * Constructor is private, so no-one can initialise one explicitly
	 */
	CLogger();

	/**
	 * Destructor
	 */
	~CLogger();

public:
	static void Info(std::string const & tag, std::string const & msg);
	static void Debug(std::string const & tag, std::string const & msg);
	static void Warn(std::string  const & tag, std::string const & msg);
	static void Error(std::string const & tag, std::string const & msg);

	static void EnablePublishing();
	static void DisablePublishing();

protected:
	//// Thread-Safe ////
	/**
	 * \brief Provides a locked wrapper around the Log function
	 */
	inline void LockLog(timeval const & call_time, const int level, std::string const & tag, std::string const & msg)
	{
		pthread_mutex_lock(&(_this().mLock));
		Log_(call_time, level, tag, msg);
		pthread_mutex_unlock(&(_this().mLock));
	}

	static CLogger & _this();

private:
	static void * Refresh(void * this_ptr);	//!< Used to periodically flush buffer to prevent data loss (implements a thread)

	//// Not-Thread-Safe: must be wrapped in locks ////
	void InitialiseOutputFile();
	void ResolveOutputFile();
	void Log_(timeval const & call_time, const int level, std::string const & tag, std::string const & msg);

	//// Not-Thread-Safe but does not use any class members themselves////
	static timeval getTime();


	//============== Member Variables ================//
private:
	std::ofstream  		mPersist;	//Persistent storage to file
	time_t				mMidnight;	//Indication of next midnight (at which point, data gets saved to a different file)
	zmq::context_t		mContext;	//ZMQ Context
	zmq::socket_t *		mSocketPtr;	//Socket for publishing to...
	pthread_mutex_t		mLock;		//For Ensuring Thread-Safety
	pthread_t			mRefresh;	//Refresh thread
	bool				mTerminate;	//Indicates to the refresh thread that we are terminating
};

/**
 * \brief  Macro Definitions for Fast Access
 * \detail The class must implement a TAG variable
 */

#define _INFO(msg)  CLogger::Info(TAG, msg)
#define _DEBUG(msg) CLogger::Debug(TAG, msg)
#define _WARN(msg)  CLogger::Warn(TAG, msg)
#define _ERROR(msg) CLogger::Error(TAG, msg)



/**
 * \brief  Macro definition for the Log file Name
 * \detail ONLY one call to this macro can be performed per executable
 */
#define LOG_FOLDER(folder) const std::string CLogger::DEBUG_FOLDER = #folder;



#endif /* INCLUDE_LOGGER_H_ */
