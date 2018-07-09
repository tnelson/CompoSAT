# CompoSAT

## Setup

Clone both 

- https://github.com/sorawee/CompoSAT
- https://github.com/sorawee/AmalgamKodkod

to your Eclipse's workspace

The only support platforms are:

- amd64-linux
- amd64-windows
- x86-mac

## How to use

Run `./gui`

To enumerate instances using CompoSAT, use "CompoSAT Next" in the visualizer dialog. 
Note that the first output model is not a part of CompoSAT's output. 
As such, it is possible that models output by CompoSAT will contain the first output model.

CompoSAT currently doesn't support interleaving between regular "Next" and "CompoSAT Next"
(or any other options such as Shrink and Grow). That is, make sure that you only use
"CompoSAT Next".

The time limit for the internal enumeration is 20 seconds.

To see CompoSAT logs, set the Alloy's verbosity level to "debug"

