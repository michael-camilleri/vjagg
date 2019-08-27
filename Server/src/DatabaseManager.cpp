/*
 * DatabaseManager.cpp
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

#include "include/DatabaseManager.h"
#include "include/ConnectionManager.h"

#define LOG_LOGGER

#include <sys/stat.h>
#include <unistd.h>

#include <MUL++/include/Utilities.h>
#include <MCF++/include/Logger.h>


//////////////////////////////////////////////////////////////////////////////////////////////

RoutePoint_t::RoutePoint_t()
{
	mLatitude = mLongitude = 0.0;
	mTimeSt = -1;
}

std::ostream& operator<< (std::ostream &strm, RoutePoint_t const & point)
{
	strm << point.mTimeSt << " " << point.mLatitude << " " << point.mLongitude;
	return strm;
}

std::istream& operator>> (std::istream &strm, RoutePoint_t &point)
{
	strm >> point.mTimeSt >> point.mLatitude >> point.mLongitude;
	return strm;
}

//////////////////////////////////////////////////////////////////////////////////////////////

Journey_t::Journey_t()
{
	mJourneyID = -1;		//Empty Journey
	mMode	   = MD_UNSPEC;
	mPurpose   = TP_UNSPEC;
	mTermCode  = TC_UNSPEC;
}

bool Journey_t::LoadFromMsg(std::istringstream & msg_strm)
{
	int32_t		 tmp_int;
	RoutePoint_t tmp_pnt;

	//Read in Header Information (except for the Journey ID which would be already read)
	msg_strm >> mMode >> mPurpose >> mTermCode;

	//Read in actual journey
	msg_strm >> tmp_int;
	mRoutePts.clear(); mRoutePts.reserve(tmp_int);
	for (int i=0; i<tmp_int; i++) { msg_strm >> tmp_pnt; mRoutePts.push_back(tmp_pnt); }

	return true;
}

bool Journey_t::StoreToFile(std::ofstream & file_strm)
{
	//Store Journey (including size)
	file_strm << mJourneyID << " " << mMode << " " << mPurpose << " " << mTermCode << mRoutePts.size() << "\n";

	//Push in actual journey points
	for (uint i=0; i<mRoutePts.size(); i++) { file_strm << "\n" << mRoutePts[i]; }

	return true;
}

//////////////////////////////////////////////////////////////////////////////////////////////

const std::string UserRecord_t::USER_DATA_FILE = "UserData.dat";
const std::string UserRecord_t::JOURNEY_FOLDER = "Journeys";
const std::string UserRecord_t::JOURNEY_SEP_STUB = "%Y-%m";
const std::string UserRecord_t::JOURNEY_FILE_STUB = "[%d-%m-%Y](%H-%M-%S).dat";

const std::string UserRecord_t::TAG = "User Record";

UserRecord_t::UserRecord_t()
{
	mID 		= "DefaultUser";
	mHashCode   = 0;				//Invalid Value
	mUserFolder = "";				//Empty String
	mLastAccess = -1;
	mDynamic	= NULL;
}

UserRecord_t::~UserRecord_t()
{
	if (mDynamic != NULL) { delete(mDynamic); } //Clean up
}

bool UserRecord_t::LoadCore(std::ifstream & file_strm)
{
	file_strm >> mID;
	file_strm >> mHashCode;
	file_strm >> mLastAccess;
	std::getline(file_strm, mUserFolder); mUserFolder = trim(mUserFolder);	//Removing leading/trailing whitespace

	DEBUG("Loaded Record: " + mID);
	return true;
}

bool UserRecord_t::StoreRecord(std::ofstream & file_strm)
{
	//Store Core Data
	file_strm << mID << " " << mHashCode << " " << mLastAccess << " " << mUserFolder;

	//Store any dynamic data
	return StoreDynamic();
}

bool UserRecord_t::SetUserData(std::string const & user_folder, std::string const & msg_data)
{
	DEBUG("Setting User Data for " + mID);

	//For parsing
	std::istringstream strm(msg_data);
	uint32_t    	   tmp_int;

	//If not initialised, create new one, else reuse...
	if (mDynamic == NULL) { mDynamic = new DynamicValues_t(); }
	else				  { WARN("User Data was previously set for " + mID); }

	//Set User Folder & create directories: if unsuccessful, return false
	if ((mkdir((user_folder + "/" + JOURNEY_FOLDER).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { ERROR("Could not create User Folder"); return false; }
	mUserFolder = user_folder;

	//Parse and fill in constant values
	strm >> tmp_int; mDynamic->mGender = static_cast<Gender_t>(tmp_int);
	strm >> tmp_int; mDynamic->mAgeGroup = static_cast<AgeGroup_t>(tmp_int);
	strm >> tmp_int; mDynamic->mPosition = static_cast<Position_t>(tmp_int);
	strm >> mDynamic->mCarAccess;

	//Initialise the Dynamic Values (list of journeys)
	mDynamic->mJourneyList.clear();

	//Register Time
	mLastAccess = time(NULL);	//Set to current time (in seconds)

	DEBUG("User Data Initialised for " + mID);
	//Return success
	return true;
}

UserRecord_t & UserRecord_t::operator=(const UserRecord_t &rhs)
{
	//First copy the core values
	mID        	= rhs.mID;
	mHashCode 	= rhs.mHashCode;
	mLastAccess = rhs.mLastAccess;
	mUserFolder	= rhs.mUserFolder;

	//Now handle the Dynamic loading
	if (mDynamic != NULL) { delete mDynamic; mDynamic = NULL; }	//Prevent Memory Leaks
	if (rhs.mDynamic != NULL)
	{
		mDynamic = new DynamicValues_t;	//Create new one
		mDynamic->mGender 	= rhs.mDynamic->mGender;
		mDynamic->mAgeGroup = rhs.mDynamic->mAgeGroup;
		mDynamic->mPosition = rhs.mDynamic->mPosition;
		mDynamic->mCarAccess = rhs.mDynamic->mCarAccess;
		mDynamic->mJourneyList = rhs.mDynamic->mJourneyList;
	}

	//Return reference to self
	return *this;
}


bool UserRecord_t::AddJourney(std::istringstream & msg_data)
{
	std::ofstream  		file_strm;	//File stream
	Journey_t			journey;	//Journey
	char 				buffer[40]; //Temporary Buffer
	time_t 				journey_name;	//Temporary journey start index

	DEBUG("Adding Journey for user " + mID);

	if (mUserFolder.size() < 1) { ERROR("User Folder Unknown!"); return false; }

	//Just in case, load
	if (!LoadDynamic()) { ERROR("Could not Load Dynamic Data for user"); return false; }

	//Read in the journey id & check that inexistent
	DEBUG("Msg Data - " + msg_data.str());
	msg_data >> journey.mJourneyID;
	if (findJourney(journey.mJourneyID)) { WARN("Journey [" + to_String(journey.mJourneyID) + "] already exists for User"); return false; }

	//Else read in the rest of the journey (and check that correctly loaded)
	if (journey.LoadFromMsg(msg_data) != true) { ERROR("Journey incorrectly formatted."); return false; }

	//Now store the journey - but first need to locate start time...
	journey_name = journey.mJourneyID/1000;	//Changed to use the Journey ID as easier for lookup...
	strftime(buffer, 40, JOURNEY_SEP_STUB.c_str(), localtime(&journey_name));

	//See if folder exists and if not create it...
	if ((mkdir((mUserFolder + "/" + JOURNEY_FOLDER + "/" + buffer).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { ERROR("Could not create User Folder."); return false; }

	//Now store the actual journey
	strftime(buffer, 40, (JOURNEY_SEP_STUB + "/" + JOURNEY_FILE_STUB).c_str(), localtime(&journey_name));
	file_strm.open((mUserFolder + "/" + JOURNEY_FOLDER + "/" + buffer).c_str(), std::ios::trunc);
	if (!file_strm.is_open())	{ ERROR("Could not create User Journey File"); return false; }
	file_strm.precision(10);
	if (!journey.StoreToFile(file_strm)) { file_strm.close(); ERROR("Could not store Journey"); return false; }

	//Register journey in list
	mDynamic->mJourneyList.push_back(journey.mJourneyID);

	DEBUG("Journey Successfully Registered");
	return true;
}

bool UserRecord_t::ValidateJourney(uint64_t journey_id)
{
	if (!LoadDynamic()) { return false; }
	else		 		{ return !findJourney(journey_id); }
}

Gender_t UserRecord_t::getGender()
{
	if (!LoadDynamic()) { return Gender_t::Male; }
	else		 { return mDynamic->mGender; }
}

AgeGroup_t UserRecord_t::getAgeGroup()
{
	if (!LoadDynamic()) { return AgeGroup_t::Age_Under25; }
	else		 		{ return mDynamic->mAgeGroup; }
}

Position_t UserRecord_t::getPosition()
{
	if (!LoadDynamic()) { return Position_t::Student; }
	else 		 { return mDynamic->mPosition; }
}

bool UserRecord_t::getCarAccess()
{
	if (!LoadDynamic()) { return false; }
	else 		 { return mDynamic->mCarAccess; }
}

void UserRecord_t::SelectiveUnload()
{
	//Check if anything to store and if timeout is indeed passed
	if ((mDynamic != NULL) && ((time(NULL) - mLastAccess) > CACHE_TIMEOUT))
	{
		DEBUG("User Data for " + mID + " being unloaded due to TimeOut");
		StoreDynamic();
		delete(mDynamic);
		mDynamic = NULL;
	}
}

bool UserRecord_t::LoadDynamic()
{
	mLastAccess = time(NULL);

	//Check that we are initialised
	if (mUserFolder.size() < 1) { ERROR("LoadDynamic - User Folder Unknown"); return false; }

	//If not loaded, load
	if (mDynamic == NULL) //Not yet loaded
	{
		DEBUG("Loading Dynamic Data for " + mID);
		std::ifstream file_strm;
		uint32_t	  tmp_int;
		uint64_t	  tmp_lng;

		file_strm.open((mUserFolder + "/" + USER_DATA_FILE).c_str());
		if (!file_strm.is_open())	{ ERROR("Could not open User File"); return false; }

		mDynamic = new DynamicValues_t;

		//Read in Headers
		file_strm >> tmp_int; mDynamic->mGender = static_cast<Gender_t>(tmp_int);
		file_strm >> tmp_int; mDynamic->mAgeGroup = static_cast<AgeGroup_t>(tmp_int);
		file_strm >> tmp_int; mDynamic->mPosition = static_cast<Position_t>(tmp_int);
		file_strm >> mDynamic->mCarAccess;

		//Read in List of Journeys
		file_strm >> tmp_int;
		for (uint32_t i=0; i<tmp_int; i++) { file_strm >> tmp_lng; mDynamic->mJourneyList.push_back(tmp_lng); }
		file_strm.close();

		DEBUG("Dynamic Data Loaded");
	}

	return true;
}

bool UserRecord_t::findJourney(uint64_t journey_id)
{
	return (std::find(mDynamic->mJourneyList.begin(), mDynamic->mJourneyList.end(), journey_id) != mDynamic->mJourneyList.end());
}

bool UserRecord_t::StoreDynamic()
{
	//Check that initialised
	if (mUserFolder.size() < 1) { ERROR("StoreDynamic - User Folder Unknown"); return false; }

	//Check that something to store and store if so..
	if (mDynamic != NULL)
	{
		DEBUG("Storing Dynamic Parameters for " + mID);

		std::ofstream file_strm;

		file_strm.open((mUserFolder + "/" + USER_DATA_FILE).c_str(), std::ios::trunc);
		if (!file_strm.is_open())	{ ERROR ("StoreDynamic - Could not open File Stream"); return false; }

		//Write Header
		file_strm << mDynamic->mGender << " " << mDynamic->mAgeGroup << " " << mDynamic->mPosition << " " << mDynamic->mCarAccess << std::endl;

		//Write Journeys
		file_strm << mDynamic->mJourneyList.size() << std::endl;
		for (int i=0; i<mDynamic->mJourneyList.size(); i++) { file_strm << mDynamic->mJourneyList[i] << "\n"; }

		//Close file
		file_strm.close();

		DEBUG("Dynamic Parameters stored");
	}

	return true;
}


//////////////////////////////////////////////////////////////////////////////////////////////
const std::string CDatabaseManager::DATA_FOLDER 		= "UserData";		//!< Data Folder
const std::string CDatabaseManager::USER_LIST_FILE 		= "UserList.dat";	//!< File containing the User Records (as subset of DATA_FOLDER)
const std::string CDatabaseManager::USER_FOLDER_STUB 	= "[%06d]";			//!< User Folder Stub: the user hash-code in 6-leading-zeros format. (as subset of DATA_FOLDER)

const std::string CDatabaseManager::LOG_FOLDER			= "UserLogs";							//!< Log Parent Folder
const std::string CDatabaseManager::LOG_SEP_STUB		= "%Y-%m-%d";							//!< Log Separation Stub (based on week of year)
const std::string CDatabaseManager::LOG_STUB_FILE		= "[%d-%m-%Y](%H-%M-%S){%%06d}.log";	//!< File stub containing the name format for Log Files (organised by Week, as subset of LOG_FOLDER) [Contains two levels of parsing, first the date then the user hashcode]

const std::string CDatabaseManager::TAG				   	= "Database Manager";


///TODO Add Versioning Control ////

CDatabaseManager::CDatabaseManager(zmq::context_t & context) :
	mContext(context)
{
	//Seed Random Generator to current time
	srand(time(NULL));

	//Initialise Variables
	mControlThread = 0;

	INFO("Initialised DataBase Manager");
}

CDatabaseManager::~CDatabaseManager()
{
	if (mControlThread > 0)
	{
		WARN("Database Servicing Thread was not closed gracefully!");
		pthread_cancel(mControlThread);
	}
}

bool CDatabaseManager::Init()
{
	//Ensure that one thread only is ever started...
	if (mControlThread > 0) { WARN("Cannot start DBase service: already running!"); return false; }	//Cannot start an already started service

	//else start control thread
	return (pthread_create(&mControlThread, NULL, ServiceRequests, this) == 0);
}

void CDatabaseManager::WaitForCompletion()
{
	DEBUG("Waiting for Completion of Database Thread...");
	if (mControlThread > 0) { pthread_join(mControlThread, NULL); mControlThread = 0; }
	INFO("Database Manager terminating.");
}

void * CDatabaseManager::ServiceRequests(void * class_ptr)
{
	CDatabaseManager * _this = static_cast<CDatabaseManager *>(class_ptr);
	bool end = false;

	INFO("Entered Database Manager Servicing Thread");

	//Create ZMQ Control Socket
	CZMQSocket control_socket(_this->mContext, ZMQ_PAIR, DATABASE_CONTROL, true);	//Will be a pair to exclude further controls
	try { control_socket.Connect();	}   //Bind to the control topic
	catch(zmq::error_t const & e) { ERROR(std::string(e.what()).append(" : Database Thread Terminating.")); return NULL; }
	DEBUG("Bound to Control Socket");

	//Create ZMQ RPC Socket
	CZMQSocket service_socket(_this->mContext, ZMQ_REP, DATABASE_SERVICE, true);	//Reply Type (Remote Procedure Call)
	try { service_socket.Connect(); }	//Bind to the service topic
	catch(zmq::error_t const & e) { ERROR(std::string(e.what()).append(" : Database Thread Terminating.")); control_socket.SendMsg(CMessage::TerminateMsg()); return NULL; }
	DEBUG("Set up Service Socket");

	//Prepare all data and send notification to main thread
	DEBUG("Attempting to Load Database");
	if (!_this->LoadDatabase()) { ERROR("Could not load database"); control_socket.SendMsg(CMessage::TerminateMsg()); return NULL; }
	else						{ control_socket.SendMsg(CMessage::OKMsg()); }

	//Create Watchdog for saving of data on a regular basis:
	WatchdogTimer save_users(300000);	//Save user map every 5 minutes
	WatchdogTimer cache_users(60000);	//Unload unused data every minute

	//Loop for servicing requests...
	INFO("Starting Database Manager Servicing Loop");
	while (!end)
	{
		CMessage ctrl;			//Actual control message

		//First poll on the main command socket - The only command it understands so far is the terminate
		ctrl = control_socket.ReadMsg(false);
		switch(ctrl.mCode)	//Switch based on the control code
		{
			case CMessage::CM_TERM: //Close our own socket, but first send OK
				INFO("Database Manager Received Termination Message");
				control_socket.SendMsg(CMessage::OKMsg());
				end = true;	//Now end
				break;

			case CMessage::CM_EMPTY:
				//Do nothing
				break;

			default:
				WARN("Database Manager Received invalid Request [" + to_String(ctrl.mCode) + "]");
				control_socket.SendMsg(CMessage::InvalidMsg());
				break;
		}

		if (end) { break; } //Extra precaution: break from loop

		//Now poll on the Service Socket
		ctrl = service_socket.ReadMsg(false);
		switch(ctrl.mCode)	//Switch based on the control code
		{
			case CConnectionManager::TMAR_IDENT_REQ:	//Request for a new identity for the user
			{
				INFO("Requested a New Identity");
				//First create new user and assign hashcode
				UserRecord_t new_user;
				new_user.mHashCode = _this->mUserList.size()+1;	//Size of the map indicates hashcode

				//Now create User Folder
				char buffer[20]; sprintf(buffer, USER_FOLDER_STUB.c_str(), new_user.mHashCode);
				if ((mkdir((DATA_FOLDER + "/" + buffer).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) != 0) { ERROR("Could not Create User Folder"); service_socket.SendMsg(CMessage::FailureMsg("Folder Creation Failed.")); break; }

				//(else) Now update actual user data
				if (!new_user.SetUserData((DATA_FOLDER + "/" + buffer), ctrl.mData)) { ERROR("Could not create User File"); service_socket.SendMsg(CMessage::FailureMsg("User Data Parsing Failed.")); break; }

				//Finally store in Map
				_this->mUserList[_this->GenerateUserID(new_user)] = new_user;

				//Return success
				ctrl.mCode = CConnectionManager::TMAR_IDENT_REP;
				ctrl.mData = new_user.mID;
				service_socket.SendMsg(ctrl);
				DEBUG("Identity Generated");
			}
			break;

			case CConnectionManager::TMAR_IDENT_JOUR_VER: //Request for verification  of ID & Journey Tag
				INFO("Request for Identity Verification with Journey Validation");
				switch(_this->CheckID(ctrl.mData, true))
				{
					case 1:
						INFO("Identity & Journey Verified");
						ctrl.mCode = CMessage::CM_OK;
						ctrl.mData = "";
						break;

					case 0:
						ctrl.mCode = CMessage::CM_FAIL;
						ctrl.mData = "Inexistent ID";
						break;

					default:
						ctrl.mCode = CMessage::CM_FAIL;
						ctrl.mData = "Journey Duplicate";
						break;
				}
				service_socket.SendMsg(ctrl);
			break;

			case CConnectionManager::TMAR_IDENT_VER:	//Request for verification of ID
				INFO("Request for Identity Verification");
				switch(_this->CheckID(ctrl.mData, false))
				{
					case 0:
						ctrl.mCode = CMessage::CM_FAIL;
						ctrl.mData = "Inexistent ID";
						break;

					default:
						INFO("Identity Verified");
						ctrl.mCode = CMessage::CM_OK;
						ctrl.mData = "";
						break;
				}
				service_socket.SendMsg(ctrl);
			break;

			case CConnectionManager::TMAR_STORE_JOURNEY:
				INFO("Request to Store Journey");
				if (_this->StoreJourney(ctrl.mData))
				{
					INFO("Journey Stored - " + ctrl.mData);
					ctrl.mCode = CConnectionManager::TMAR_JOURNEY_RECEIVED;
					ctrl.mData = "";
				}
				else
				{
					ctrl.mCode = CMessage::CM_FAIL;
					ctrl.mData = "Could Not Store Journey.";
				}
				service_socket.SendMsg(ctrl);
			break;

			case CConnectionManager::TMAR_STORE_LOG:
				INFO("Request to Store Log");
				if (_this->StoreLog(ctrl.mData))
				{
					INFO("Log Stored");
					ctrl.mCode = CConnectionManager::TMAR_LOG_RECEIVED;
					ctrl.mData = "";
				}
				else
				{
					ctrl.mCode = CMessage::CM_FAIL;
					ctrl.mData = "Could Not Store Log.";
				}
				service_socket.SendMsg(ctrl);
			break;

			case CMessage::CM_EMPTY:
				//Do nothing
				break;

			default:
				WARN("Received invalid Request from client ["+to_String(ctrl.mCode) + "]");
				service_socket.SendMsg(CMessage::InvalidMsg());
				break;
		}

		//Save the database if need be...
		if (cache_users.expired())
		{
			for (auto it = _this->mUserList.begin(); it != _this->mUserList.end(); it++)
			{
				it->second.SelectiveUnload();
			}
			cache_users.ping();
		}

		if (save_users.expired())
		{
			if (!_this->StoreDatabase()) { WARN("Unable to Backup Users"); }
			save_users.ping();
		}

		//Finally sleep for a couple milliseconds to avoid stressing the CPU
		usleep(LOOP_RATE);
	}

	//Clean Up
	if (!_this->StoreDatabase()) { ERROR("Unable to Store the User List"); }
	else						 { INFO("Database Loop Terminating"); }

	return NULL;
}


bool CDatabaseManager::LoadDatabase()
{
	DEBUG("Loading Database");

	//Initialise
	std::ifstream file_strm;

	//Clear the Database so far
	mUserList.clear();

	//Setup User Data System
	if ((mkdir(DATA_FOLDER.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { ERROR("Could not create Data Folder with Error [" + to_String(errno) + "]"); return false; }

	//Load User List
	file_strm.open((DATA_FOLDER + "/" + USER_LIST_FILE).c_str());
	if (!file_strm.good())	//If inexistent, then create
	{
		DEBUG("Folder Inexistent: Re-creating");
		std::ofstream file_test;
		file_test.open((DATA_FOLDER + "/" + USER_LIST_FILE).c_str(), std::ios::trunc);
		if (!file_test.good()) { ERROR("Could not Create UserList File"); return false; }
	}
	else
	{
		DEBUG("Reading previously saved User List");
		UserRecord_t tmp_user;
		std::string  version;

		//Read in the version
		file_strm >> version;

		//Read in User Records
		while (!file_strm.eof())
		{
			if (tmp_user.LoadCore(file_strm)) { mUserList[tmp_user.mID] = tmp_user; }
			else							  { ERROR("Error in Reading User Record"); return false; }	//We have an issue
		}
	}

	//Close User File stream
	file_strm.close();

	//Now set up the Log File Directory
	if ((mkdir(LOG_FOLDER.c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { ERROR("Could not Create Log Folder"); return false; }

	DEBUG("Database Loaded");

	//If everything ok, return
	return true;
}


bool CDatabaseManager::StoreDatabase()
{
	DEBUG("Storing Database");
	std::ofstream file_strm;

	//Open file with Overwriting (and fail if unsuccessful)
	file_strm.open((DATA_FOLDER + "/" + USER_LIST_FILE).c_str(), std::ios::trunc);
	if (file_strm.fail()) { ERROR("Unable to open User List File"); return false; }

	//Write the Version
	file_strm << "2.0.0";

	//Store the records
	for (auto it = mUserList.begin(); it!=mUserList.end(); it++)
	{
		file_strm << "\n";	//Write newline
		if (!it->second.StoreRecord(file_strm)) { ERROR("Unable to Store User [" + it->second.mID + "]"); return false; }
	}

	file_strm.close();
	DEBUG("Database Stored");
	return true;
}


std::string CDatabaseManager::GenerateUserID(UserRecord_t & user)
{
	bool search = true;

	//Search for unique id
	while (search)
	{
		//First character is male or female
		user.mID = user.getGender() == Male ? "M":"F";

		//Next come 4 random characters...
		user.mID.append(RandomAlphaNumeric(4));

		//Now come two age-governed alpha-numeric
		user.mID.append(RandomAlphaNumeric(user.getAgeGroup(), 2));

		//Next come 3 more random characters...
		user.mID.append(RandomAlphaNumeric(3));

		//Now come two position-governed alpha-numeric
		user.mID.append(RandomAlphaNumeric(user.getPosition(), 2));

		//Next come 3 more random characters...
		user.mID.append(RandomAlphaNumeric(3));

		//Finally close with the access to car flag
		user.mID.append((user.getCarAccess() ? "Y":"N"));

		//Check whether it exists... and keep search if it does (i.e. it is found)
		search = (mUserList.find(user.mID) != mUserList.end());
	}

	return user.mID;
}

int32_t CDatabaseManager::CheckID(const std::string & msg_data, bool check_journey)
{
	DEBUG("Checking ID");
	std::istringstream 								strm(msg_data);
	std::string		   								user_id;
	std::map<std::string, UserRecord_t>::iterator 	rec_it;
	uint64_t										journey_id;

	//Read in the User ID first
	strm >> user_id;
	if ((rec_it = mUserList.find(user_id)) == mUserList.end())
	{
		WARN("DatabaseManager: ID [" + user_id + "] does not exist...");
		return 0; //ID does not exist
	}

	//Read in the Journey ID
	if (check_journey)
	{
		strm >> journey_id;
		if (!(rec_it->second.ValidateJourney(journey_id)))
		{
			WARN("Journey " + to_String(journey_id) + " for User " + user_id + " already exists");
			return -1;	//Journey already logged
		}
	}

	//else
	return 1;
}


bool CDatabaseManager::StoreJourney(std::string const & msg_data)
{
	DEBUG("Storing Journey");
	//Variables
	std::istringstream 	str_strm(msg_data);	//String Stream for parsing msg data to remove the user id.
	std::string 		user_id;			//Storage for user id

	//Parse the User ID
	str_strm >> user_id;	//Read in the user ID

	//StoreDynamic the Journey for this user
	return mUserList[user_id].AddJourney(str_strm);
}


//TODO Improve Logging Framework by putting a Vector of Vectors of Log Data Pointers (from User HashCodes to Log Files)
bool CDatabaseManager::StoreLog(std::string const & msg_data)
{
	DEBUG("Storing Log");
	//Variables
	std::istringstream 	str_strm(msg_data);		//String Stream for parsing msg data to remove the user id.
	std::ofstream  		file_strm;				//File stream
	std::string			user_id;				//Temporary storage of user id
	std::string			tmp_str;				//Temporary file_path
	char 				folder[20], file[40];	//Character buffers for parsing filename

	//Register the Current Time
	time_t current_time = time(NULL);

	//Construct Directory structure (See if folder exists and if not create it...)
	strftime(folder, 20, LOG_SEP_STUB.c_str(), localtime(&current_time));
	if ((mkdir((LOG_FOLDER + "/" + folder).c_str(), S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH) != 0) && (errno != EEXIST)) { ERROR("Could Not Create Log Separation Folder"); return false; }
	DEBUG("Built Directory");

	//Now build the User File Name, bit by bit
	str_strm >> user_id;
	strftime(file, 40, LOG_STUB_FILE.c_str(), localtime(&current_time));			//Convert time to file name
	tmp_str = file; sprintf(file, tmp_str.c_str(), mUserList[user_id].mHashCode);	//Append user hashcode

	file_strm.open((LOG_FOLDER + "/" + folder + "/" + file).c_str(), std::ios::trunc);
	if (!file_strm.good()) { ERROR("Could not create Log File"); return false; }
	DEBUG("Built User File");

	//Parse in the Log Data
	std::getline(str_strm, tmp_str);
	find_replace(tmp_str, "[", "\n["); //Replace all square-brackets with newline and square brackets
	DEBUG("Parsed Log Data");

	//Store to file
	file_strm << user_id << " " << tmp_str;

	//Close and return
	file_strm.close();

	DEBUG("Log File Saved");
	return true;
}

std::string CDatabaseManager::RandomAlphaNumeric(int length)
{
	std::string random(length, ' ');

	for (int i=0; i<length; i++)
	{
		char value = rand()%62;

		if (value < 10) 		{ value += 48; }	//Number
		else if (value < 36)	{ value += 55; }	//UpperCase Letter (10 should be A...)
		else					{ value += 61; }    //LowerCase Letter (36 should be a...)

		random[i] = value;
	}

	return random;
}

std::string CDatabaseManager::RandomAlphaNumeric(int type, int length)
{
	std::string random(length, ' ');

	switch(type)
	{
		case 0:	//Use a number
			for (int i=0; i<length; i++) { random[i] = (rand()%10 + 48); }
			break;

		case 1:
			for (int i=0; i<length; i++) { random[i] = (rand()%13 + 65); }
			break;

		case 2:
			for (int i=0; i<length; i++) { random[i] = (rand()%13 + 78); }
			break;

		case 3:
			for (int i=0; i<length; i++) { random[i] = (rand()%13 + 97); }
			break;

		default:
			for (int i=0; i<length; i++) { random[i] = (rand()%13 + 110); }
			break;
	}

	return random;
}
