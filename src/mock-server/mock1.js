var http = require('http');
var mockserver = require('mockserver');

http.createServer(mockserver('mock1')).listen(9001);