/*
 * Socket.cpp
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


#include <openssl/err.h>
#include <sys/fcntl.h>
#include <arpa/inet.h>
#include <netinet/tcp.h>
#include <netdb.h>
#include <unistd.h>

#define LOG_LOGGER

#include <MUL++/include/zhelpers.hpp>
#include <MUL++/include/Utilities.h>

#include "../include/Socket.h"
#include "../include/Logger.h"

//TODO consider that the real-sockets can implement as a single select function...

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

ISocket::ISocket()
{
	//Empty Constructor
}

ISocket::~ISocket()
{
	//Empty Destructor
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

CZMQSocket::CZMQSocket(zmq::context_t & context, uint16_t type, std::string const & address, bool bind) :
	mContext(context),
	mSocket(context, type),
	mAddress(address),
	mType(type),
	mBind(bind)
{
	mConnx = false;
}

CZMQSocket::~CZMQSocket()
{
	Close();
}

bool CZMQSocket::Connect()
{
	try
	{
		if (mBind) 	{ mSocket.bind(mAddress); _DEBUG("Bound"); }
		else	  	{ mSocket.connect(mAddress); _DEBUG("Connected"); }
		mConnx = true;
	}
	catch(zmq::error_t & e) { _ERROR("Unable to connect socket"); return false; }

	return true;
}

bool CZMQSocket::SetOption(int option, const void * value, size_t size)
{
	mSocket.setsockopt(option, value, size);
	return true;
}

void CZMQSocket::Close()
{
	if (mConnx)
	{
		int linger = 1000;	//Linger for 1 second at most...
		mSocket.setsockopt(ZMQ_LINGER, &linger, sizeof(linger));
		mSocket.close(); mConnx = false;
	}
}

CMessage CZMQSocket::ReadMsg(bool block)
{
	CMessage ctrl;
	zmq::message_t msg;

	if(mSocket.recv(&msg, (block ? 0 : ZMQ_DONTWAIT)) == true) //Receive non-blocking or blocking...
	{
		ctrl.serialize(std::string(static_cast<char*>(msg.data()), msg.size()));
	}

	return ctrl;
}

CIDMessage CZMQSocket::ReadIDMsg(bool block)
{
	CIDMessage idmsg;
	zmq::message_t msg;

	if(mSocket.recv(&msg, (block ? 0 : ZMQ_DONTWAIT)) == true) //Receive non-blocking or blocking...
	{
		//Read in Identity (just the last 4 bytes, since the first byte is always 0)
		memcpy(&(idmsg.mIdentity), static_cast<uint8_t*>(msg.data())+1, 4);

		//Ensure that there is more to receive TODO consider removing once system is tried and tested
		int64_t more; size_t more_size = sizeof more; mSocket.getsockopt(ZMQ_RCVMORE, &more, &more_size);
		if (more < 1)
		{
			idmsg.mMessage.mCode = CMessage::CM_FAIL;
			_ERROR("IDMsg Err");
			return idmsg;
		}
		//Otherwise, clear zmq_msg and read in the data message
		msg.rebuild(); mSocket.recv(&msg, 0);

		//Serialize message
		idmsg.mMessage.serialize(std::string(static_cast<char*>(msg.data()), msg.size()));
	}
	return idmsg;
}

bool CZMQSocket::SendMsg(const CMessage & msg)
{
	return s_send(mSocket, msg.serialize());
}

bool CZMQSocket::SendMsg(const std::string & msg)
{
	return s_send(mSocket, msg);
}

bool CZMQSocket::SendIDMsg(const CIDMessage & msg)
{
	zmq::message_t ident(5);	// 5-Byte Identity
	*(static_cast<uint8_t*>(ident.data())) = 0;	//First byte is empty
	memcpy (static_cast<uint8_t*>(ident.data())+1, &msg.mIdentity, 4);
	if (!mSocket.send(ident, ZMQ_SNDMORE)) { return false; }

	return s_send(mSocket, msg.mMessage.serialize());
}

bool CZMQSocket::SendIDMsg(uint32_t id, const CMessage & msg)
{
	zmq::message_t ident(5);	// 5-Byte Identity
	*(static_cast<uint8_t*>(ident.data())) = 0;	//First byte is empty
	memcpy (static_cast<uint8_t*>(ident.data())+1, &id, 4);
	if (!mSocket.send(ident, ZMQ_SNDMORE)) { return false; }

	return s_send(mSocket, msg.serialize());
}

bool CZMQSocket::SendIDMsg(uint32_t id, const std::string & msg)
{
	zmq::message_t ident(5);	// 5-Byte Identity
	*(static_cast<uint8_t*>(ident.data())) = 0;	//First byte is empty
	memcpy (static_cast<uint8_t*>(ident.data())+1, &id, 4);
	if (!mSocket.send(ident, ZMQ_SNDMORE)) { return false; }

	return s_send(mSocket, msg);
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

CSSLSocket::CSSLSocket(std::string const & cert_file, int64_t sock_fd)
{
	//First initialize all variables
	mSSLContext  = NULL;
	mSSLState    = NULL;
	mConnx       = sock_fd;
	mCertFile	 = cert_file;
}

CSSLSocket::~CSSLSocket()
{
	Close();
}


bool CSSLSocket::Connect()
{
	/*Create new context from method - return immediately if unsuccessful*/
	if ((mSSLContext = SSL_CTX_new(SSLv23_method())) == NULL) { _ERROR("SSL_CTX_new failure."); return false; }
	SSL_CTX_set_mode(mSSLContext, SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
	SSL_CTX_set_mode(mSSLContext, SSL_MODE_ENABLE_PARTIAL_WRITE);

	//Load certificates (and again fail if unsuccessful)
	if (SSL_CTX_use_certificate_chain_file(mSSLContext, mCertFile.c_str()) <= 0) 			 { _ERROR("Certificate failure."); return false; }
	if (SSL_CTX_use_PrivateKey_file(mSSLContext, mCertFile.c_str(), SSL_FILETYPE_PEM) <= 0 ) { _ERROR("Private Key failure."); return false; }
	if (SSL_CTX_check_private_key(mSSLContext) == false) 									 { _ERROR("Check Key failure."); return false; }

	//Create SSL State from context...
	if ((mSSLState = SSL_new(mSSLContext)) == NULL) 										 { _ERROR("SSL_new failure."); return false; }

	//Attempt to set up receive timeout
	timeval read_timeout = {5,0};
	setsockopt(mConnx, SOL_SOCKET, SO_RCVTIMEO, &read_timeout, sizeof(read_timeout));

	//Accept secure connection
	if (SSL_set_fd(mSSLState, mConnx) == 0) { Close(); _ERROR("SSL_set_fd failure."); return false; } //Connect socket to SSL
	if (SSL_accept(mSSLState) < 0)			{ Close(); _ERROR("SSL_accept failure."); return false; }

	//Modify underlying socket to non-blocking and using TCP NODELAY
	fcntl(mConnx, F_SETFL, O_NONBLOCK);						//Set to non-blocking
	int flag = 1;
	if (setsockopt(mConnx, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag)) != 0) { Close(); _ERROR("setsockopt failure."); return false; }	//Set to always write and not buffer...

	return true;
}

void CSSLSocket::Close()
{
	_DEBUG("Calling Close");
	if (mSSLState != NULL) 	{ SSL_free(mSSLState); mSSLState = NULL; }
	if (mConnx > 0) 		{ close(mConnx);       mConnx = -1; }
	if (mSSLContext != NULL){ SSL_CTX_free(mSSLContext); mSSLContext = NULL; }
}

CMessage CSSLSocket::ReadMsg(bool block)
{
	//Create Message (Empty)
	CMessage msg;
	msg.mCode = CMessage::CM_EMPTY;	//Initially empty

	//For non-blocking reads...
	fd_set  accept_fd;
	FD_ZERO(&accept_fd);
	FD_SET(mConnx, &accept_fd);
	timeval delay; delay.tv_sec  = 0; delay.tv_usec = 1;	//Wait for 1 us (to avoid hogging CPU)

	select(mConnx + 1, &accept_fd, NULL, NULL, (block ? NULL : &delay));
	if (FD_ISSET(mConnx, &accept_fd))	//If something to do
	{
		int num_read = SSL_read(mSSLState, mBuffer, BUFFER_SIZE-1);	//Read up to buffer size - 1
		std::string msg_data = "";

		while (num_read > 0)
		{
			msg_data.append(std::string(static_cast<char*>(mBuffer), num_read));
			num_read = SSL_read(mSSLState, mBuffer, BUFFER_SIZE-1);	//Read up to buffer size - 1
		}

		if (msg_data.length() > 0) { msg.serialize(msg_data); }
	}

	return msg;
}

bool CSSLSocket::SendMsg(const CMessage & msg)
{
	std::string tmp_buf = msg.serialize() + '\n';	//Will require extra newline...
	return (SSL_write(mSSLState, tmp_buf.c_str(), tmp_buf.length()) > 0);
}

bool CSSLSocket::SendMsg(const std::string & msg)
{
	std::string tmp_buf = msg + '\n';
	return (SSL_write(mSSLState, tmp_buf.c_str(), tmp_buf.length()) > 0);
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

CTCPSocket::CTCPSocket(int64_t sock_fd) :
		mAddr(""), mPort(0)
{
	//First initialize all variables
	mConnx       = sock_fd;
}

CTCPSocket::CTCPSocket(std::string const & address, int port) :
		mAddr(address), mPort(port)
{
	mConnx = -1;
}

CTCPSocket::~CTCPSocket()
{
	Close();
}

bool CTCPSocket::Connect()
{
	if (mConnx < 0) //Thefore this is a client & we still need to connect
	{
		//Prepare Hints of how we want to connect
		struct addrinfo hints, *result;
		memset(&hints, 0, sizeof(struct addrinfo));
		hints.ai_family   = AF_UNSPEC;    	/* Allow IPv4 or IPv6 */
		hints.ai_socktype = SOCK_STREAM; 	/* TCP socket */
		hints.ai_flags 	  = 0;
		hints.ai_protocol = 0;          	/* Any protocol */
		if (getaddrinfo(mAddr.c_str(), to_String(mPort).c_str(), &hints, &result) != 0) { _ERROR("AddrInfo Fail."); return false; }

		//Iterate over all possible connections
		bool connected = false;
		for (auto rp = result; rp != NULL; rp = rp->ai_next)
		{
			if ((mConnx = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol)) < 0) { continue; }					//This was unsuccessful
			if (connect(mConnx, rp->ai_addr, rp->ai_addrlen) != -1) 					{ connected = true; break; }	//This was successful
		    close(mConnx);
		}
		freeaddrinfo(result); /* No longer needed */
		if (!connected) { _ERROR("Addr Resoltn Fail"); mConnx = -1; return false; }
	}

	//Attempt to set up receive timeout
	timeval read_timeout = {5,0};
	setsockopt(mConnx, SOL_SOCKET, SO_RCVTIMEO, &read_timeout, sizeof(read_timeout));

	//Modify underlying socket to non-blocking and using TCP NODELAY
	fcntl(mConnx, F_SETFL, O_NONBLOCK);						//Set to non-blocking
	int flag = 1;
	if (setsockopt(mConnx, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag)) != 0) { _ERROR("Non-Block Fail - " + to_String(errno)); Close(); return false; }	//Set to always write and not buffer...
	_DEBUG("Connx OK");
	return true;
}

bool CTCPSocket::SetLinger(bool enable, int linger_time)
{
	struct linger so_linger;
	so_linger.l_onoff  = enable;
	so_linger.l_linger = linger_time;

	return setsockopt(mConnx, SOL_SOCKET, SO_LINGER, &so_linger, sizeof so_linger);
}

void CTCPSocket::Close()
{
	if (mConnx > 0) { close(mConnx); mConnx = -1; }
}

//TODO This architecture may need to be re-factored, especially with regards to reading from a message-less stream...
CMessage CTCPSocket::ReadMsg(bool block)
{
	//Create Message (Empty)
	CMessage msg;
	msg.mCode = CMessage::CM_EMPTY;	//Initially empty

	//For non-blocking reads...
	fd_set  accept_fd;
	FD_ZERO(&accept_fd);
	FD_SET(mConnx, &accept_fd);
	timeval delay; delay.tv_sec  = 0; delay.tv_usec = 1;	//Wait for 1 us (to avoid hogging CPU)

	select(mConnx + 1, &accept_fd, NULL, NULL, (block ? NULL : &delay));
	if (FD_ISSET(mConnx, &accept_fd))	//If something to do
	{
		int num_read = read(mConnx, mBuffer, BUFFER_SIZE-1);
		std::string msg_data = "";

		while (num_read > 0)
		{
			msg_data.append(std::string(static_cast<char*>(mBuffer), num_read));
			num_read = read(mConnx, mBuffer, BUFFER_SIZE-1);	//Read up to buffer size - 1
		}

		if (msg_data.length() > 0) { msg.serialize(msg_data); }
	}

	return msg;
}

bool CTCPSocket::SendMsg(const CMessage & msg)
{
	std::string tmp_buf = msg.serialize() + '\n';	//Will require extra newline...
	return ((send(mConnx, tmp_buf.c_str(), tmp_buf.length(), MSG_NOSIGNAL)) > 0);
}

bool CTCPSocket::SendMsg(const std::string & msg)
{
	std::string tmp_buf = msg + '\n';
	return ((send(mConnx, tmp_buf.c_str(), tmp_buf.length(), MSG_NOSIGNAL)) > 0);
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


CUDPSocket::CUDPSocket(std::string const & address, int port) :
		mAddr(address),
		mPort(port)
{
	mSckt = -1;
	memset((char *)&mOther, 0, sizeof(mOther));
}

CUDPSocket::~CUDPSocket()
{
	if (mSckt > 0)
	{
		close(mSckt);
	}
}

bool CUDPSocket::Connect()
{
	if (mSckt > 0) 									  { _WARN("Already Connected"); return false; }

	if ((mSckt = socket(AF_INET, SOCK_DGRAM, 0)) < 0) { _ERROR("Unable to Create Socket"); return false; }

	if (mAddr.size() == 0) //Then this is a server...
	{
		//Create Address to Bind to...
		struct sockaddr_in address;
		memset(&address, 0, sizeof(address));
		address.sin_family = AF_INET;
		address.sin_addr.s_addr = INADDR_ANY;
		address.sin_port = htons(mPort);

		//Attempt to bind
		if (bind(mSckt, (struct sockaddr *)&address, sizeof(address)) < 0) { _ERROR("Unable to Bind Socket : (" + to_String(errno)  + ") " + std::string(strerror(errno))); close(mSckt); mSckt = -1; return false; }
	}
	else
	{
		//First resolve the Address to 'connect' to...
		struct hostent *hp = gethostbyname(mAddr.c_str());
		if (!hp) { _ERROR("Unable to Resolve Address"); close(mSckt); mSckt = -1; return false; }

		//Keep track of the server's address...
		memcpy((void *)&mOther.sin_addr, hp->h_addr_list[0], hp->h_length);

		//Add the Port and Protocol information
		mOther.sin_family = AF_INET;
		mOther.sin_port = htons(mPort);
	}

	//Set Socket to non-blocking
	fcntl(mSckt, F_SETFL, O_NONBLOCK);

	//If all is well....
	return true;
}

void CUDPSocket::Close()
{
	if (mSckt > 0) { close(mSckt); mSckt = -1; }
}

CMessage CUDPSocket::ReadMsg(bool block)
{
	//Create Message (Empty)
	CMessage msg;
	msg.mCode = CMessage::CM_EMPTY;	//Initially empty

	//For non-blocking reads...
	fd_set  accept_fd;
	FD_ZERO(&accept_fd);
	FD_SET(mSckt, &accept_fd);
	timeval delay; delay.tv_sec  = 0; delay.tv_usec = 1;	//Wait for 1 us (to avoid hogging CPU)

	select(mSckt + 1, &accept_fd, NULL, NULL, (block ? NULL : &delay));
	if (FD_ISSET(mSckt, &accept_fd))	//If something to do
	{
		socklen_t addrlen = sizeof(mOther);

		int num_read = recvfrom(mSckt, mBuffer, BUFFER_SIZE-1, 0, (struct sockaddr *)&mOther, &addrlen);
		if (num_read > 0)
		{
			mBuffer[num_read] = 0;
			msg.serialize(mBuffer);
		}
		else
		{
			if ((errno != EAGAIN) && (errno != EWOULDBLOCK)){ msg.mCode = CMessage::CM_FAIL; }
		}
	}

	return msg;
}

bool CUDPSocket::SendMsg(const CMessage & msg)
{
	std::string tmp_buf = msg.serialize();	//No need for extra newline...
	return (sendto(mSckt, tmp_buf.c_str(), tmp_buf.size(), 0, (struct sockaddr *)&mOther, sizeof(mOther)) > 0);
}

bool CUDPSocket::SendMsg(const std::string & msg)
{
	return (sendto(mSckt, msg.c_str(), msg.size(), 0, (struct sockaddr *)&mOther, sizeof(mOther)) > 0);
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


CListener::CListener(int queue_length)
{
	mSocket = -1;
	mQueue = queue_length;
}

CListener::~CListener()
{
	if (mSocket > 0)
	{
		struct linger _linger {true, 2};	//Linger for 2 seconds
		if (setsockopt(mSocket, SOL_SOCKET, SO_LINGER, &_linger, sizeof(_linger)) < 0) { _ERROR("Linger Fail (" + to_String(errno) + ")"); }
		close(mSocket);
	}
}


bool CListener::SetupListener(uint32_t port)
{
	//Fail if we are already listening
	if (mSocket > 0) { return false; }

	//Initialize Socket
	if ((mSocket = socket(AF_INET, SOCK_STREAM, 0)) < 0) { _ERROR("Sckt Err"); return false; }

	//Create Socket Address
	struct sockaddr_in address;
	memset(&address, 0, sizeof(address));
	address.sin_family = AF_INET;
	address.sin_port = htons(port);
	address.sin_addr.s_addr = INADDR_ANY;

	//Attempt binding
	if (bind(mSocket, (struct sockaddr *)(&address), sizeof(address)) != 0) { _ERROR("Bind Err"); return false; }
	if (listen(mSocket, mQueue) != 0)										{ _ERROR("Listen Err"); return false; }
	fcntl(mSocket, F_SETFL, O_NONBLOCK);//Set to non-blocking

	return true;
}

int64_t CListener::CheckForConnection(std::string & client_ip, int64_t timeout)
{
	//Ensure that we are connected
	if (mSocket < 1) { _ERROR("Inv Sckt");  return R_FAIL; }

	//Setup File Descriptor Set
	fd_set  accept_fd;
	FD_ZERO(&accept_fd);
	FD_SET(mSocket, &accept_fd);

	//Check for blocking
	timeval delay;
	if (timeout > 0)
	{
		delay.tv_sec  = timeout/1000000;
		delay.tv_usec = timeout%1000000;
	}
	else
	{
		delay.tv_sec  = 0;
		delay.tv_usec = 1;
	}
	select(mSocket + 1, &accept_fd, NULL, NULL, &delay);
	if (!FD_ISSET(mSocket, &accept_fd)) { return R_IDLE; }	//Return that nothing to do...

	//Accept client
	sockaddr_in client;
	uint32_t	len = sizeof(client);
	int64_t 	clSock = accept(mSocket, (sockaddr *)&client, &len);	//Accept the connection

	//verify client values and return
	if (clSock < 1) { _ERROR("Accept Fail"); return R_FAIL; }
	client_ip = inet_ntoa(client.sin_addr);
	return clSock;
}

bool CListener::OK()
{
	return (mSocket > 0);
}

