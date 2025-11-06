# http-server

## Directory structure
- All server-related classes are in the top-level http-server directory
- www-root folder contains resources to be served by the server
- cgi folder contains cgi-related files

## How to run the server
The server runs on port 1223 by default (no config specified).

Usage: java server [-c|-config] <config_file_name>

## Description of server structure

I use a symmetric design such that all worker threads (dispatchers) handle accept, read, and write. When a new connection is available, all waiting dispatchers are notified, and whichever manages to accept it first will add it to its selector. The design also includes a management thread that stores pointers to all dispatchers in an array. The management thread listens for input on stdin, and whenever it receives the command "shutdown", it interrupts all dispatcher threads, which then exit at the start of the next iteration of their selection loop.

## Current progress
Part 1A is almost completely implemented: 
- methods (GET and POST)
- headers and semantics (Host, Accept, User-Agent, If-Modified-Since, Connection, Content-Length, Content-Type)
- CGI, request content parsing, and CGI response content piping
- success and error responses with all relevant headers, status codes and messages
- server configuration and virtual hosts
- content selection
Only authorization and chunked transfer encoding are not yet implemented.

Part 1B is mostly implemented: 
- n multiplexing loops
- management thread to handle graceful shutdown
- load URL
Only timeout is not yet implemented.

## Testing
I have thoroughly tested all written server functionality with all of the following commands:
- Telnet: telnet localhost 1223
- Netcat: nc -C localhost 1223 < testreq.txt > out.txt
- Curl: curl http://localhost:1223/...filepath... <-H headers>
- Chrome: http://localhost:1223/...filepath...

POST/CGI was tested with the following command: curl -v -d "@cgi/post.data" -X POST -H "Content-Type: application/json" http://localhost:1223/cgi/price.cgi

I performed throughput benchmarking with the following command: ab -n 10000 -c 10 http://localhost:6789/index_files/jnp3rd.jpg

Concurrency Level:      10

Time taken for tests:   9.722 seconds

Complete requests:      10000

Failed requests:        0

Total transferred:      1070450000 bytes

HTML transferred:       1068610000 bytes

Requests per second:    1028.56 [#/sec] (mean)

Time per request:       9.722 [ms] (mean)

Time per request:       0.972 [ms] (mean, across all concurrent requests)

Transfer rate:          107521.96 [Kbytes/sec] received
