/*
 * DatabaseManager.h
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
 *  Created on: Feb 23, 2016
 *      Author: michael
 */

#ifndef INCLUDE_DATABASEMANAGER_H_
#define INCLUDE_DATABASEMANAGER_H_

//TODO convert static const strings to constexpr...

#include <pthread.h>
#include <fstream>
#include <string>
#include <vector>
#include <ctime>
#include <map>

#include <MCF++/include/Socket.h>

#include "Debug.h"

/**
 * Gender Data Type
 */
enum Gender_t
{
	Male   = 0,	//!< Male
	Female = 1	//!< Female
};

/**
 * Age Groups
 */
enum AgeGroup_t
{
	Age_Teen 	= 0,//!< Teenager [16-17]
	Age_Under25	= 1,//!< Under 25 [18-24]
	YngAdult	= 2,//!< Young Adult [25-40]
	Adult		= 3,//!< Adult [41-60]
	Elderly		= 4	//!< Elderly [61+]
};

/**
 * University Position
 */
enum Position_t
{
	Student 	= 0,	//!< Student
	Academic 	= 1,	//!< Academic Staff
	Administ	= 2,	//!< Administrative Staff
	Visitor		= 3,	//!< Visitor
	None		= 4		//!< No specific relation to University
};

enum TripMode_t
{
	MD_UNSPEC  = 0,   //!< Unspecified (Can be zero, since if none are identified)
    MD_CAR_DRV = 1,   //!< Car Driver
    MD_CAR_PAS = 2,   //!< Car Passenger
    MD_BUS     = 4,   //!< Bus (Public Transport)
    MD_MINIBUS = 8,   //!< Coach or Minibus
    MD_MOTOR   = 16,  //!< Motorbike
    MD_FOOT    = 32,  //!< On foot
    MD_BIKE    = 64,  //!< Bicycle
    MD_FERRY   = 128, //!< Ferry
    MD_TAXI    = 256, //!< Taxi
    MD_OTHER   = 512, //!< Other
};

enum TripPurpose_t
{
    TP_UNSPEC  = 0, //!< Unspecified
    TP_HOME    = 1,  //!< Going Home
    TP_VISIT   = 2,  //!< Visiting Someone
    TP_TO_WORK = 4,  //!< Going to work
    TP_WRK_REL = 8,  //!< Work Related trip
    TP_MEDICAL = 16,  //!< For Medical Reasons
    TP_PERSON  = 32,  //!< Personal Errands
    TP_SHOP    = 64,  //!< Shopping
    TP_EDUC    = 128,  //!< To school, Junior College, University
    TP_ACCOMP  = 256,  //!< Accompanying someone
	TP_SPORT   = 512,  //!< For Sporting Reasons
    TP_OTHER   = 1024  //!< Other reason
};

enum Termination_t
{
	TC_UNSPEC =  0,    //Undefined (typically journey has not ended yet)
	TC_RT_CN  =  1,    //Route Continues
    TC_RE_NM  =  2,    //Termination Code: Route Ends because there was no significant motion
    TC_RE_SL  =  3,    //Termination Code: Route Ends because there was an extended loss of signal...
    TC_RE_SF  =  4,    //Termination Code: Route Ends because there was an extended loss of signal but a tentative termination point was found...
    TC_RE_US  =  5,    //Termination Code: Route ended by the user (through the destroy function)
    TC_RE_OS  =  6     //Termination Code: Route ended by the OS or an error
};

struct RoutePoint_t
{
public:
	double 		mLatitude;
	double 		mLongitude;
	int64_t 	mTimeSt;

	//========== Member Functions ===============//
public:
	/**
	 * \brief Default Constructor
	 */
	RoutePoint_t();
};

struct Journey_t
{
	//========== Member Variables =============//
public:
	int64_t		 				mJourneyID;	//!< The unique Journey ID
	int64_t						mMode;		//!< Trip Mode (generic integer)
	int64_t						mPurpose;	//!< The purpose for the trip
	int64_t						mTermCode;	//!< Termination Reason (again general integer)

	std::vector<RoutePoint_t>	mRoutePts;	//!< The actual Route Points

	//========== Member Functions ===============//
public:
	/**
	 * \brief Constructor
	 */
	Journey_t();

	/**
	 * \brief Load the Journey from a string stream of data
	 */
	bool LoadFromMsg(std::istringstream & msg_strm);

	/**
	 * \brief Store the Entire Journey to file
	 */
	bool StoreToFile(std::ofstream & file_strm);
};


struct DynamicValues_t
{
public:
	Gender_t    			mGender;		//!< The user gender
	AgeGroup_t				mAgeGroup;		//!< Age Group
	Position_t  			mPosition;		//!< Position/relation to the university
	bool					mCarAccess;		//!< Access to car (true or false)
	std::vector<uint64_t> 	mJourneyList;	//!< The list of journeys
};

/**
 * \brief Encapsulates the data associated with each user
 * The class is not thread-safe: i.e. it must be accessed from one thread!
 */
class UserRecord_t
{
	//=========== Member Constants =============//
public:
	static const uint32_t CACHE_TIMEOUT = 120;	//!< Cache timeout (for SelectiveUnload) in seconds

protected:
	static const std::string USER_DATA_FILE;	//!< User Data File: Fixed constant
	static const std::string JOURNEY_FOLDER;	//!< User Journey Folder (within which the journeys exist)
	static const std::string JOURNEY_SEP_STUB;  //!< Stub Folder for the (monthly) separation of journeys
	static const std::string JOURNEY_FILE_STUB;	//!< The File Stub for the User Journeys

private:
	static const std::string TAG;				//!< Debug log

	//=========== Member Variables =============//
	//Core Values - These are public and can be accessed anytime
public:
	std::string mID;			//!< The unique user id... this is the identifier for all cases
	uint32_t	mHashCode;		//!< The User HashCode (shorter file name) [From 1 updwards]

	//Dynamic Values - These are private and can be accessed only if loaded
protected:
	std::time_t		  mLastAccess;	//!< Time of last access of this record...(seconds since epoch)
	DynamicValues_t * mDynamic;		//!< Indicates whether the user data is fully loaded from file (will be null if not)

private:
	std::string 	  mUserFolder;	//!< Reference to the user folder

	//=========== Member Functions ===============//
public:
	/**
	 * \brief Default Constructor
	 */
	UserRecord_t();

	/**
	 * \brief Destructor
	 */
	~UserRecord_t();

	/**
	 * \brief Loads the Core Values from File (only)
	 */
	bool LoadCore(std::ifstream & file_strm);

	/**
	 * \brief Stores the record to file, including both Static and Dynamic parameters if available
	 */
	bool StoreRecord(std::ofstream & file_strm);

	//============ Setter Functions =============//
public:
	/**
	 * \brief Sets the User Record data from the String message data...
	 * @param user_folder String containing the user folder where all data will be stored.
	 * @param msg_data 	  String containing, in order, the gender, age-group, position, and car access flags
	 */
	bool SetUserData(std::string const & user_folder, std::string const & msg_data);

	/**
	 * \brief Add a Journey to the User's data
	 * @param msg_data The Journey data, prefixed by the journey identifier
	 */
	bool AddJourney(std::istringstream & msg_data);

	/**
	 * \brief Check if Journey ID valid (i.e. it exists
	 */
	bool ValidateJourney(uint64_t journey_id);

	/**
	 * \brief Overloads the Assignment operator to correctly handle the null pointers etc...
	 */
	UserRecord_t & operator = (const UserRecord_t &rhs);

	//=========== Accessor Functions ============//
public:
	Gender_t	getGender();
	AgeGroup_t	getAgeGroup();
	Position_t	getPosition();
	bool		getCarAccess();

	//=========== Lifetime Management ===========//
public:
	/**
	 * \brief 	Called to unload the data if not used for an extended amount of time.
	 */
	void SelectiveUnload();

private:
	/**
	 * \brief If the user record is loaded, then it just updates the last access and does nothing. Else it loads from file
	 * @return Fails if the UserFolder is not set... returns false in this case.
	 */
	bool LoadDynamic();

	//=========== Helper Functions ============// Note that these do not automaically load the data
protected:
	/**
	 * \brief Checks if the requested journey id exists...
	 * @param  journey_id The ID of the journey requested
	 * @return True if it exists, false otherwise
	 */
	bool findJourney(uint64_t journey_id);

	/**
	 * \brief	Stores the Data to file (only the dynamic, which is managed directly by the user object)
	 */
	bool StoreDynamic();
};


/**
 * The Database Manager Class encapsulates the Data Collection Database...
 */
class CDatabaseManager
{
	/////////////////////////////////// Static Constants //////////////////////////////////////////
public:
	static const std::string DATA_FOLDER;				//!< Data Folder
	static const std::string USER_LIST_FILE;			//!< File containing the User Records (as subset of DATA_FOLDER)
	static const std::string USER_FOLDER_STUB;			//!< User Folder Stub: will be suffixed with the user hash-code in 6-leading-zeros format. (as subset of DATA_FOLDER)

	static const std::string LOG_FOLDER;				//!< Log Parent Folder
	static const std::string LOG_SEP_STUB;				//!< Log Separation Stub (based on week of year)
	static const std::string LOG_STUB_FILE;				//!< File stub containing the common name for all user log files - these are prefixed with the file ID (organised by Month, as subset of LOG_FOLDER)

	static constexpr auto DATABASE_SERVICE = "inproc://database_server.ipc";			//!< Topic for REQ-REP communication with clients
	static constexpr auto DATABASE_CONTROL = "ipc:///tmp/database_control.ipc";			//!< Control Topic


private:
	static const uint32_t	 DATABASE_VERSION = 100;	//!< Database Version code...
	static const uint32_t	 LOOP_RATE		  = 10000;	//!< 10ms loop rate (sleep)
	static const std::string TAG;

	///////////////////////////////////// Initialisers ///////////////////////////////////////////
public:
	CDatabaseManager(zmq::context_t & context);

	~CDatabaseManager();

	/**
	 * \brief Starts the Database Service Routine and returns...
	 * The service can be terminated via a message call
	 */
	bool Init();

	/**
	 * \brief Waits until Database closes ...
	 */
	void WaitForCompletion();

	////////////////////////////////// Servicing functions ////////////////////////////////////////////
private:
	/**
	 * \brief Static Void member function for handling requests in another thread
	 */
	static void * ServiceRequests(void * class_ptr);

	///////////////////////////////////// Helper Functions //////////////////////////////////////////
private:
	/**
	 * \brief Loads the Database from file
	 * NOTE: This is not thread-safe: i.e. must be called only from within the ServiceRequests() function
	 * @return True if successful, false otherwise
	 */
	bool LoadDatabase();

	/**
	 * \brief Stores the Database to file
	 * NOTE: This is not thread-safe: i.e. must be called only from within the ServiceRequests() function
	 * @return True if successful, false otherwise
	 */
	bool StoreDatabase();

	/*
	 * \brief Generates a new unique identifier for the user
	 * NOTE: This is not thread-safe: i.e. must be called only from within the ServiceRequests() function
	 * The id is both stored in the user record and returned as a string...
	 */
	std::string GenerateUserID(UserRecord_t & user);

	/**
	 * \brief Checks whether the given user ID exists and if so whether the journey has already been logged...
	 * @return 1 if ok; 0 if ID does not exist; -1 if ID exists but Journey was already logged...
	 */
	int32_t CheckID(const std::string & msg_data, bool check_journey);

	/**
	 * \brief Stores the new journey to file and logs a reference to it in the user records...
	 * NOTE: This is not thread-safe: i.e. must be called only from within the ServiceRequests() function
	 * The msg_data expects to be the user_id followed by the journey information... this should be serialised as per the journey object
	 */
	bool StoreJourney(std::string const & msg_data);

	/**
	 * \brief Stores the Log file associated with a user...
	 * NOTE: This is not thread-safe: i.e. must be called only from within the ServiceRequests() function
	 * The msg_data should contain the user_id as its first element...
	 */
	bool StoreLog(std::string const & msg_data);

	/**
	 * \brief Returns a random alpha-numeric character
	 */
	std::string RandomAlphaNumeric(int length);

	/**
	 * \brief Returns a random alpha-numeric character, based on the type (0 - number, 1 - first half of UpperCase, 2 -second half of UpperCase, 3 - first half of LowerCase, 4 - second half of LowerCase
	 */
	std::string RandomAlphaNumeric(int type, int length);


	///////////////////////////////////// Member Variables //////////////////////////////////////////
private:
	zmq::context_t &	mContext;		//!< Reference to the underlying context - may need to check if this is valid
	pthread_t			mControlThread; //!< The thread for servicing requests

	/// ==== Thread Variables ==== ///
	std::map<std::string, UserRecord_t>	 mUserList;	//!< Maps user id's to User Records...
};

//=====================================================================================================================//
	/**
	 * \brief  Serialize values to output stream
	 * @return Stream for chaining
	 */
	std::ostream& operator<< (std::ostream &strm, RoutePoint_t const & point);

	/**
	 * \brief Serialize from an input stream
	 * @return       True if successful, false otherwise
	 */
	std::istream& operator>> (std::istream &strm, RoutePoint_t &point);


#endif /* INCLUDE_DATABASEMANAGER_H_ */
