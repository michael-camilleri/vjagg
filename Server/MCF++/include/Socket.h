/*
 * Socket.h
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
 *      Author: Michael Camilleri
 *      Defines the Key Interface for all socket types (be it secure, tcp, udp zmq)
 */

#ifndef INCLUDE_CSOCKET_H_
#define INCLUDE_CSOCKET_H_

#include <string>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <openssl/ssl.h>

#include <MUL++/include/zmq.hpp>

#include "Message.h"

#define BUFFER_SIZE 2048	//2KB Buffer Size

/**
 * \brief A Message definition which also has an ID
 * \detail This is designed to be used by Router ZMQ Sockets. Since I envision connecting Dealer to Router Sockets only
 * 		   (without REQ/REP), then I am designing my own protocol. Each Message will contain an Identity (uniquely generated
 * 		   by the Router Socket) and a Message Part. For now I am foregoing the actual sequence ID (instead putting the
 * 		   responsibility for correct reply identification on the higher-level protocol designer)
 */
class CIDMessage
{
public:
	uint32_t	mIdentity;
	CMessage	mMessage;
};

/**
 * \brief Base Interface from which all sockets inherit
 */
class ISocket
{
	//============== Initialisers ==============//
public:
	/**
	 * \brief Initialiser Constructor
	 */
	explicit ISocket();

	/**
	 * \brief Destructor
	 */
	virtual ~ISocket();

	//=========== Connection Control ===========//
public:
	/**
	 * \brief Connects the socket
	 */
	virtual bool Connect() = 0;

	/**
	 * \brief Closes the connection
	 */
	virtual void Close() = 0;

	//=============== I/O Control ==============//
public:
	/**
	 * \brief Pure Virtual Function to Read a Message (and return it as a CMessage object)
	 * @param block Indicates if the underlying read should block or not... Note that this will not have any effect if the socket itself is in blocking mode
	 */
	virtual CMessage ReadMsg(bool block) = 0;

	/**
	 * \brief Function to Send a Message
	 * @param msg CMessage object to send
	 * @return Indication of success or failure
	 */
	virtual bool SendMsg(const CMessage & msg) = 0;

	/**
	 * \brief Function to Send a Message (string variant)
	 * @param msg String containing contents to send
	 * @return Indication of success or failure
	 */
	virtual bool SendMsg(const std::string & msg) = 0;
};


class CZMQSocket : public ISocket
{
	//============== Initialisers ==============//
public:
	/**
	 * \brief Initialiser Constructor
	 * @param context The ZMQ Context to use
	 * @param type    The Type of socket (typically ZMQ_PAIR etc..)
	 * @param address The Topic to Connect/Bind to
	 * @param bind	  Indicates whether this will be a binding or connecting socket
	 */
	explicit CZMQSocket(zmq::context_t & context, uint16_t type, std::string const & address, bool bind);

	/**
	 * \brief Destructor
	 */
	virtual ~CZMQSocket();

	//=========== Connection Control ===========//
public:
	/**
	 * \brief Connects the socket
	 * @return True if successful, false otherwise
	 */
	virtual bool Connect();

	/**
	 * \brief Sets the respective socket options
	 * @param option Socket options to set
	 * @param value  Value to set option to...
	 * @param size	 Size of the value object (since passed as void pointer)
	 * @return True if successful, false otherwise
	 */
	bool SetOption(int option, const void * value, size_t size);

	/**
	 * \brief Closes the connection
	 */
	virtual void Close();

	//=============== I/O Control ==============//
public:
	virtual CMessage ReadMsg(bool block);
	virtual bool SendMsg(const CMessage & msg);
	virtual bool SendMsg(const std::string & msg);

	//For Router/Dealer Messages
	CIDMessage ReadIDMsg(bool block);
	bool SendIDMsg(const CIDMessage & msg);
	bool SendIDMsg(uint32_t id, const CMessage & msg);
	bool SendIDMsg(uint32_t id, const std::string & msg);

	//============ Member Variables ============//
protected:
	zmq::context_t & 	mContext;	//!< ZMQ Context
	zmq::socket_t  	 	mSocket;	//!< The socket itself
	const std::string	mAddress;	//!< Address of socket
	const uint16_t	   	mType;		//!< The type of socket
	const bool			mBind;		//!< Indicates whether to bind or connect

	//=============== Member Variables ==============//
private:
	bool	mConnx;	//!< Connection Indication - will be negative if not connected

private:
	static constexpr auto TAG = "CZMQSocket";
};


class CSSLSocket : public ISocket
{
public:
	/*
	 * \brief Default Constructor:
	 * \detail Initialises the CSSLSocket object, by creating the context, loading certificates etc...
	 *
	 */
	explicit CSSLSocket(std::string const & cert_file, int64_t sock_fd);

	/**
	 * Destructor - cleans up
	 */
	virtual ~CSSLSocket();


	//=========== Connection Control ===========//
public:
	/**
	 * \brief Connects the socket
	 * @return True if successful, false otherwise
	 */
	virtual bool Connect();

	/**
	 * \brief Closes the connection
	 */
	virtual void Close();

	//=============== I/O Control ==============//
public:
	virtual CMessage ReadMsg(bool block);
	virtual bool SendMsg(const CMessage & msg);
	virtual bool SendMsg(const std::string & msg);

protected:
	SSL_CTX * 		mSSLContext;	//!< The SSL Server Context used for communication (must be freed in destructor)
	SSL * 			mSSLState;		//!< SSL Connection
	std::string		mCertFile;		//!< Copy of the Certificate File (name) to use
	int64_t			mConnx;			//!< Connection Indication - will be negative if not connected

private:
	char 			mBuffer[BUFFER_SIZE];	//!<Buffer for reading into
	static constexpr auto TAG = "CSSLSocket";
};

//TODO I have a major bug here, in that I am not really taking care of the delimiter... and the buffer does not separate based on delimiters...
class CTCPSocket : public ISocket
{
	//============== Initialisers ==============//
public:
	/**
	 * \brief Initialiser Constructor
	 * \detail Two variants are provided. The first is envisioned to be used via a Listener object which creates the connection
	 * 		   automatically: the other is used to create a client connection.
	 * @param sock_fd The File Descriptor for the Socket (already opened by the listener object)
	 * @param address The address of the server to connect to...
	 * @param port    The port number to connect on...
	 */
	explicit CTCPSocket(int64_t sock_fd);
	explicit CTCPSocket(std::string const & address, int port);

	/**
	 * \brief Destructor
	 */
	virtual ~CTCPSocket();

	//=========== Connection Control ===========//
public:
	/**
	 * \brief Connects the socket - actually, socket will already be connected
	 * @return True always
	 */
	virtual bool Connect();

	/**
	 * \brief 	Sets the SO Linger flag
	 * \detail 	Should be called with linger off when the server is initiating a connection close...
	 * @param	enable 		Boolean variable to enable or disable lingering
	 * @param   linger_time The time to linger for (if desired)
	 */
	bool SetLinger(bool enable, int linger_time = 0);

	/**
	 * \brief Closes the connection
	 */
	virtual void Close();

	//=============== I/O Control ==============//
public:
	virtual CMessage ReadMsg(bool block);
	virtual bool SendMsg(const CMessage & msg);
	virtual bool SendMsg(const std::string & msg);

private:
	char mBuffer[BUFFER_SIZE];	//!<Buffer for reading into
	const std::string mAddr;
	const int         mPort;
	int64_t			  mConnx;	//!< Connection Indication - will be negative if not connected

	static constexpr auto TAG = "CTCPSocket";
};

/**
 * \brief Implements a simple UPD Socket Wrapper
 * TODO Currently, we only support a 1-1 connection: i.e. server can send back to the same client and only after a client has sent something...
 * TODO We are also limit to BUFFER_SIZE-length messages
 */
class CUDPSocket : public ISocket
{
	//============== Initialisers ==============//
public:
	/**
	 * \brief 	Initialiser Constructor
	 * @param address The address of the server to connect to... If left empty, then this is a server...
	 * @param port    The port number to connect on...
	 */
	explicit CUDPSocket(std::string const & address, int port);

	/**
	 * \brief Destructor
	 */
	virtual ~CUDPSocket();

	//=========== Connection Control ===========//
public:
	/**
	 * \brief Connects the socket
	 * \detail This only applies to server side-sockets, in which case the socket is bound. Otherwise, nothing happens
	 * @return True always
	 */
	virtual bool Connect();

	/**
	 * \brief Closes the connection (just releases resources)
	 */
	virtual void Close();

	//=============== I/O Control ==============//
public:
	//These are typically used by the client socket... since there is no notion of an identifier
	virtual CMessage ReadMsg(bool block);
	virtual bool SendMsg(const CMessage & msg);
	virtual bool SendMsg(const std::string & msg);

//	//These are used by the server... uses an identifier which relates server address to a unique number.
//	CIDMessage ReadIDMsg(bool block);
//	bool SendIDMsg(const CIDMessage & msg);
//	bool SendIDMsg(uint32_t id, const CMessage & msg);
//	bool SendIDMsg(uint32_t id, const std::string & msg);

private:
	char mBuffer[BUFFER_SIZE];	//!<Buffer for reading into

	const std::string  mAddr;				//!< The address of the server to send to
	const int          mPort;				//!< The port to communicate on
	int64_t			   mSckt;				//!< The socket
	struct sockaddr_in mOther;				//!< The other side of the connection

//	std::map<std::string, uint32_t> mIP2ID;	//!< Mapping from IP Addresses to identifiers
//	std::map<uint32_t, std::string> mID2IP;	//!< Mapping from identifiers to IP Addresses

	static constexpr auto TAG = "CUDPSocket";
};

/**
 * Encapsulates a listening socket for creating TCP connections
 */
class CListener
{
public:
	static constexpr int64_t R_IDLE = -1;	//!< Indicate IDLE
	static constexpr int64_t R_FAIL = -2;	//!< Indicate FAILURE

public:
	/**
	 * \brief Constructor
	 * @param queue_length The queue length for pending customers
	 * @param delay		   The blocking delay to wait for (in ms)
	 */
	CListener(int queue_length);
	~CListener();

	/**
	 * Start listening on the specified port
	 */
	bool SetupListener(uint32_t port);

	/**
	 * \brief Check (non-blocking) for a pending connection
	 * \detail The method checks the underlying listener socket for any pending connections and if any, serves the next one in line.
	 * 		   The socket file descriptor is returned: otherwise, -1 is returned, indicating that it is idle. The function may block
	 * 		   for the specified timeout period if requested. Otherwise, it selects based on a 1us timeout...
	 * @param[out] client_ip Used to return the client ip address of the connecting socket
	 * @param[in]  timeout   The timeout period (in microseconds)
	 */
	int64_t CheckForConnection(std::string & client_ip, int64_t timeout = -1);

	/**
	 * \brief Health indicator
	 * @return True if connected and alive, false otherwise.
	 */
	bool OK();

private:
	int 	mSocket;	//!< Socket Identifier over which we are listening
	int 	mQueue;		//!< The queue length


private:
	static constexpr auto TAG = "CListener";
};



#endif /* INCLUDE_CSOCKET_H_ */
