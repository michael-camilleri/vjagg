/**
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
 *      Author: Michael P Camilleri
 */

#ifndef INCLUDE_UTILITIES_H_
#define INCLUDE_UTILITIES_H_

#include <iostream>
#include <signal.h>
#include <unistd.h>
#include <sstream>
#include <cstdint>
#include <iomanip>
#include <string>
#include <vector>
#include <map>

#define deg2rad(deg) (deg)*M_PI/180.0
#define rad2deg(rad) (rad)*180.0/M_PI

#define RADIUS_EARTH 6372797	//!< Radius of the Earth (in m)

inline std::string to_Upper(std::string const & str)
{
	std::string tmp_buf = "";

	for (uint32_t i=0; i<str.size(); i++)
	{
		tmp_buf += toupper(str[i]);
	}

	return tmp_buf;
}

inline std::string to_String(const uint32_t val, const uint8_t zero_pad = 0)
{
	std::ostringstream tmp_buf;
	tmp_buf << std::setfill('0') << std::setw(zero_pad) << val;
	return tmp_buf.str();
}

inline std::string to_String(const int32_t val, const uint8_t zero_pad = 0)
{
	std::ostringstream tmp_buf;
	tmp_buf << std::setfill('0') << std::setw(zero_pad) << val;
	return tmp_buf.str();
}

inline std::string to_String(const int64_t val, const uint8_t zero_pad = 0)
{
	std::ostringstream tmp_buf;
	tmp_buf << std::setfill('0') << std::setw(zero_pad) << val;
	return tmp_buf.str();
}

inline std::string to_String(const uint64_t val, const uint8_t zero_pad = 0)
{
	std::ostringstream tmp_buf;
	tmp_buf << std::setfill('0') << std::setw(zero_pad) << val;
	return tmp_buf.str();
}

inline std::string to_String(const double val, const int32_t prec = 0)
{
	std::ostringstream tmp_buf;
	if (prec > 0) { tmp_buf.precision(prec); }
	tmp_buf << val;
	return tmp_buf.str();
}

inline std::string to_String(const bool val)
{
	return (val ? "T" : "F");
}

inline uint32_t to_UINT32(const std::string & str)
{
	std::istringstream tmp_buf(str);
	uint32_t tmp_val;
	tmp_buf >> tmp_val;
	return tmp_val;
}

inline double to_Double(const std::string & str)
{
	std::istringstream tmp_buf(str);
	double tmp_val;
	tmp_buf >> tmp_val;
	return tmp_val;
}

inline std::string FormatFullMSTime(uint64_t mseconds)
{
	char buffer[30];
	time_t seconds = mseconds/1000;
	strftime(buffer, 30, "%a %d/%m/%Y %H:%M:%S.", localtime(&seconds));
	return buffer + to_String(mseconds%1000);
}

//inline std::string FormatTime(int64_t seconds)
//{
//	char buffer[15];
//	strftime(buffer, 15, "{%H:%M:%S}", localtime(&seconds));
//	return std::string(buffer);
//}

inline void find_replace(std::string & str, const std::string & find, const std::string & replace)
{
	size_t index = str.find(find, 0);	//Find the specified string starting from 0...
	while (index != std::string::npos)
	{
		str.replace(index, find.length(), replace);
		index = str.find('[', index+replace.length()+1);	//Find the character starting from index + 2 (since we added a character and we wish to skip already found...
	}
}

template<typename k, typename v>
k find_unique_key(std::map<k, v> & int_map)
{
	k unique = 0;

	for (auto it = int_map.begin(); it != int_map.end(); it++)
	{
		if (it->first != unique) { break; }
		else					 { unique++; }
	}

	return unique;
}

/**
 * \brief From http://stackoverflow.com/questions/1798112/removing-leading-and-trailing-spaces-from-a-string
 */
inline std::string trim(const std::string& str)
{
    const auto strBegin = str.find_first_not_of(" \n\t");
    if (strBegin == std::string::npos) { return ""; }// no content
    const auto strEnd = str.find_last_not_of(" \n\t");
    const auto strRange = strEnd - strBegin + 1;
    return str.substr(strBegin, strRange);
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * Watchdog Timer Object
 * \detail Provides a timing functionality with Millisecond (ms) resolution
 */
class WatchdogTimer
{
public:
	/**
	 * \brief Public Constructor
	 * \detail Sets the timeout and also enables/initialises the clock (as if pinged) if so chosen
	 * @param timeout Timeout value to use as default: Must be supplied
	 * @param enabled Dictates whether the watchdog starts enabled or not (defaults to true)
	 */
	explicit WatchdogTimer(uint32_t timeout, bool enabled = true);

	/**
	 * \brief Ping
	 */
	void ping(uint32_t timeout = 0);

	/**
	 * \brief Check if expired
	 * \detail Will return true iff mNextAlarm is passed, and the timer is itself enabled
	 */
	bool expired();

	/**
	 * \brief Disable the timer: it is re-enabled by a ping
	 */
	void disable();

private:
	uint64_t getTime();

private:
	uint32_t mTimeOut;		//!< The Timeout (in milliseconds)
	uint64_t mNextAlarm;	//!< The next alarm time
	bool 	 mEnabled;		//!< Enabled Flag (can be disabled)
};

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

/**
 * For making OpenSSL Thread-Safe - Adapted from [https://curl.haxx.se/libcurl/c/threaded-ssl.html] & [http://opensource.apple.com/source/OpenSSL/OpenSSL-16/openssl/crypto/threads/mttest.c]
 * \detail The Class implements the Singleton Pattern for consistency
 */
class SSLThreadWrapper
{
	//////////////// Initialisers ///////////////////////
private:
	/**
	 * Constructor is private, so no-one can initialise one explicitly
	 */
	SSLThreadWrapper();

	/**
	 * Destructor
	 */
	~SSLThreadWrapper();

public:
	/**
	 * This function is called to initialise the SSL Wrapper object: it handles instantiation of the in-built member variables...
	 */
	static SSLThreadWrapper * InitialiseWrapper();

	/**
	 * Function required to be implemented for using OpenSSL in threaded fashion...
	 */
	static void getLock(int mode, int type, char *file, int line);

	/**
	 * Function required to be implemented for using OpenSSL in threaded fashion...
	 */
	static unsigned long getThreadID(void);

private:
	static SSLThreadWrapper * 		_this;			//!< Pointer to an instantiation of the class - Initially null
	std::vector<pthread_mutex_t>	mLocks;			//!< I am using an std::vector for good practice
};

/**
 * Helper Function (library style)
 */
inline void OpenSSL_enable_multithreading()
{
	SSLThreadWrapper::InitialiseWrapper();
}

inline bool Disable_SigPipe()
{
	struct sigaction sa;
	sa.sa_handler = SIG_IGN;
	sa.sa_flags = 0;
	return (sigaction(SIGPIPE, &sa, 0) != -1);
}

inline void DumpCore()
{
	pid_t child = fork();

	if      (child == 0) { abort(); }
	else if (child < 0)  { std::cout << "No Fork" << std::endl; }

}

#endif /* INCLUDE_UTILITIES_H_ */
