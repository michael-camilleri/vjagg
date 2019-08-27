Michael's Communication Framework
===========================================
-------------------------------------------

MCF++ (short for Michael's Communication Framework) is a **Static** library which implements the core functionality to support message passing between programs running on different threads and general communication. We use the ZMQ framework to provide the underlying infrastructure for this functionality, as well as normal TCP sockets for network communication. The framework also implements an optional SSL layer for TCP connections.

Since this is a compiled library, using the functionality entails two steps:
1. Add a reference to the global **Library_Resources** directory within the ``-I`` Compiler settings. (On Eclipse, this can be found at ```C/C++ Build | Settings | GCC C++ Compiler | Includes | Include Paths```)
2. Add a reference to the **Release** Directory of the library within the ``-L`` Linker Options. (On Eclipse, this is done via ```C/C++ Build | Settings | GCC C++ Linker | Libraries | Library search path```)
3. Add the library within the ``-l`` Linker Options. (On Eclipse, this is achieved through ```C/C++ Build | Settings | GCC C++ Linker | Libraries | Libraries```)

Once this is done using the functionality entails only including the respective header file. For example, if instantiating a control message, then you need to include the Message Header:
```
#include <MMPF++/include/Message.h> 
```

----
#### Version History
> 1.00.00 - 06/04/16 (**First Stable Release**)
* Partitioned the Message Types: First 100 Messages will be reserved

> 2.00.00 - 05/05/16 (**Major Refactoring**)
* Rebranded as Michael's Communication Framework
* Consolidates access to all components, including message types, socket types and logger
* Messages:
  * CControlMessage renamed to CMessage (more general)
  * Added explicit default and initialiser constructors
  * Only the Generic Message codes (0-100) now form part of the framework (all others will be individually defined by respective utility)
* Sockets:
  * Implemented as a polymorphic class deriving from the ISocket interface
  * Changed order of initialisation (connect vs bind now specified at construction)
  * Added CTCPSocket and CListener helper in addition to the CZMQSocket and CSSLSocket (renaming  of CSecureServer)
* Logger:
  * Now, the only dependency of the logger is on the CMessage type: this allows it to be used within the ISocket family as well (it uses
    raw zmq_hpp support.

> 2.00.01 - 12/05/16
* Fixed a Bug in the CSSLSocket type which was causing a "double free or corruption" fault (basically, I was freeing sslstate and context twice!)
* Fixed a Bug in the logger that after the initial flushing, I would continue to flush indefinitely, since I was not updating the flush!
* Removed the mLastSave from the logger, and instead opted for a thread which periodically flushes (every 5 seconds in this case).

> 2.01.00 - 17/05/16 (**Router/Dealer Update**)
* Added a new Message Type
  * The CIDMessage encapsulates a normal CMessage as well as an ID field (string) to identify sender/receiver. This is used ot support Router/Dealer Sockets in ZMQ
* Added support for ZMQ Router and Dealer in the CZMQSocket
  * Added a new ReadIDMsg() method which reads two subsequent messages as an ID Field and a Data Message
  * Added a set of SendIDMsg() methods for writing messages with ID's.

> 2.01.01 - 02/06/16
* CZMQSocket:
  * Added some further checks within the Read-suite of messages
  * Added debug scripts
* Added some CMessage initialisation functionality

> 2.01.02 - 03-14/06/16 
* CMessage:
  * Augmented Initialisation Functionality, and ability to send Ping with Data.
* CLogger:
  * Added dynamic specification of the publishing topic...

> 2.01.03 - 15/06/16
 * CTCPSocket - Added option to set SO_LINGER

----
#### TODO List
 * Add UDP Socket support
