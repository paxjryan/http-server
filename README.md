# http-server

## Directory structure
- All server-related classes are in the top-level http-server directory
- www-root folder contains resources to be served by the server

## How to run the server
- With no config specified, the server runs on port 1223
- The config file is to be specified as the FIRST command line argument
- I have been using telnet, netcat, and Chrome web browser to make http requests to the server and debug

java server optional-config-file

- Telnet: telnet localhost 1223
- Netcat: nc -C localhost 1223 < testreq.txt > out.txt
- Chrome: http://localhost:1223/...filepath...


## Current progress
- Part 1A is almost completely implemented and tested in telnet, nc, and Chrome
- Exception: authorization and CGI/chunked transfer encoding are not yet implemented
- Parts 1B and 1C not yet implemented, though I have tried to make design decisions while implementing 1A that will facilitate part 1B, and I have most of my ideas for my server design for 1B