Version History
===============

[12/04/16] - Started History

[29/04/16] - Added a (somewhat complex) sleep cycle for the Connection Manager (Was already present for Database)
                * Basically, normal operation is at about 1 millisecond
                * This is the same for the doConnection thread (for each individual connection). However, in this case when waiting for more messages (journey or log parts), this is reduced to 10 us, but this only lasts for 10milliseconds, beyond which the normal rate is resumed, unless another message is received in the meantime.
           - Fixed bug in the find_replace function (although this is an  MUL++-specific issue)


[02/08/16] - Changed storage of Journey File to use the Journey ID instead of the Start Time...