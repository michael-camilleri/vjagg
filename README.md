# VJAGG

This repository contains all the code to go with the Paper "VJAGG - A Thick Client Smart-Phone Journey Detection Algorithm". This includes:
 1. Android and iOS Application Code for the VJAGG app.
 2. Server-Side Code for storage and retrieval of data.
 3. Code and Data to generate the Result-Figures in the paper.

## Directory Structure

The Repository contains the following:

### LICENSE
 This is the license file for this repository. We distribute our code under the GNU GPLv3 license.

### Client_Android
 This is the Development code for the Android-side application. This is written in Java using the Android IDE and development environment. Details of the algorithms are in the paper.

### Client_iOS
 This is the Development code for the iOS-side application. This is written in Swift, using the XCode development environment. The details of the algorithm are the same as for the android version up to some minor flow-control differences due to the different OS.

### Server
 This is the server code for communicating with the applications. This includes a number of statically-linked libraries/utilities. The core code is written in C++.

### Figures
 A set of scripts to generate all the Figures in the paper, together with the associated data. These are written in python (3.6).

## Troubleshooting

If you have any queries, contact the author via email at `michael.p.camilleri@ed.ac.uk`.