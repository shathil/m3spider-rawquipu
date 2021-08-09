#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdbool.h>



struct quipusocks
{
   
      struct sockaddr_in client_addr;
      struct sockaddr_in packet_addr;
}
;

struct transport_port 
{
   
     int mapped_port;
     struct quipusocks qosks;
}
;


int hashCode(int);
struct transport_port *search(int);
void insert(int, struct quipusocks);
struct transport_port *delete(struct transport_port*); 


