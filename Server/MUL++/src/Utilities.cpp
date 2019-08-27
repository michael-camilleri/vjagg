/*
 * Utilities.cpp
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
 *  Created on: May 4, 2016
 *      Author: drt_researcher
 */

#include "../include/Utilities.h"

#include <openssl/ssl.h>
#include <openssl/err.h>
#include <sys/time.h>
#include <cstddef>
#include <ctime>

WatchdogTimer::WatchdogTimer(uint32_t timeout, bool enabled)
{
	mTimeOut = timeout;
	mNextAlarm = getTime() + timeout;
	mEnabled = enabled;
}

void WatchdogTimer::ping(uint32_t timeout)
{
	mEnabled = true;
	mNextAlarm = getTime() + ((timeout > 0) ? timeout : mTimeOut);
}

bool WatchdogTimer::expired()
{
	return (mEnabled) && (getTime() > mNextAlarm);
}

void WatchdogTimer::disable()
{
	mEnabled = false;
}

uint64_t WatchdogTimer::getTime()
{
	timeval this_time;

	gettimeofday(&this_time, NULL);

	return this_time.tv_sec * 1000 + this_time.tv_usec/1000;
}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

SSLThreadWrapper * SSLThreadWrapper::_this;

SSLThreadWrapper::SSLThreadWrapper()
{
	//Initialise Lock List
	mLocks.resize(CRYPTO_num_locks());	//Allocate memory for so many locks
	for (int i=0; i<CRYPTO_num_locks(); i++) { pthread_mutex_init(&(mLocks[i]),NULL); }

	//Set Callbacks
	CRYPTO_set_id_callback((unsigned long (*)())getThreadID);
	CRYPTO_set_locking_callback((void (*)(int,int,const char *,int))getLock);
}


SSLThreadWrapper::~SSLThreadWrapper()
{
	//Remove locks
	for (int i=0; i<CRYPTO_num_locks(); i++) { pthread_mutex_destroy(&(mLocks[i])); }

	//Remove Callbacks
	CRYPTO_set_id_callback(NULL);
	CRYPTO_set_locking_callback(NULL);
}


SSLThreadWrapper * SSLThreadWrapper::InitialiseWrapper()
{
	if (_this == NULL) { _this = new SSLThreadWrapper(); }

	return _this;
}


unsigned long SSLThreadWrapper::getThreadID(void)
{
	return (unsigned long) pthread_self();
}


void SSLThreadWrapper::getLock(int mode, int type, char *file, int line)
{
	 if (mode & CRYPTO_LOCK) { pthread_mutex_lock(&(_this->mLocks[type])); }
	 else 					 { pthread_mutex_unlock(&(_this->mLocks[type])); }
}

