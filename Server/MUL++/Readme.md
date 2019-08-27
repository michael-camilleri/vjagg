Michael's Utilities Library
===========================================
-------------------------------------------

MUL++ (short for Michael's Utilities Library) is a generic utility library, consisting of a header with a number of inlined functions and an SSL Thread Wrapper. The latter provides (through a singleton construct) an efficient way of making SSL sessions using OpenSSH thread-safe through a single macro call.

For most cases, the library can be used as a header only library: the exception to this is when making use of the SSH Thread Wrapper. In the former case, using the library is as simple as adding a reference to the global **Library_Resources** directory within the Compiler options and then including it via:
```
#include <MUL++/include/Utilities.h>
```

If you need to compile against it, the process is a bit more involved:
1. Add a reference to the global **Library_Resources** directory within the ``-I`` Compiler settings. (On Eclipse, this can be found at ```C/C++ Build | Settings | GCC C++ Compiler | Includes | Include Paths```)
2. Add a reference to the **Release** Directory of the library within the ``-L`` Linker Options. (On Eclipse, this is done via ```C/C++ Build | Settings | GCC C++ Linker | Libraries | Library search path```)
3. Add the library within the ``-l`` Linker Options. (On Eclipse, this is achieved through ```C/C++ Build | Settings | GCC C++ Linker | Libraries | Libraries```)

----
#### Version History
> 1.0.0 - 06/04/16 (**First Stable Release**) 
* Added some Math Functionality

> 2.0.0 - 04/05/16 (**Major Architecture Change**)
* No longer a header-only library, due to the need for the SSLThreadWrapper

----
#### TODO List
