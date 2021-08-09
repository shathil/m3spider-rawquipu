#include <jni.h>
#include <stdio.h>
#include<errno.h>
#include<netdb.h>
#include<stdio.h>       //For standard things
#include<stdlib.h>      //malloc
#include<string.h>      //strlen

#include<pthread.h>
#include<netinet/ip_icmp.h>     //Provides declarations for icmp header
#include<netinet/udp.h> //Provides declarations for udp header
#include<netinet/tcp.h> //Provides declarations for tcp header
#include<netinet/ip.h>  //Provides declarations for ip header
#include<netinet/if_ether.h>    //For ETH_P_ALL
#include<net/ethernet.h>        //For ether_header
#include<sys/socket.h>
#include<arpa/inet.h>
#include<sys/ioctl.h>
#include<sys/time.h>
#include<sys/types.h>
#include<unistd.h>
#include <linux/filter.h>
#include <netinet/in.h>
#include <net/if.h>
#include <sys/socket.h>
#include <linux/if_packet.h>
#include <net/ethernet.h> /* the L2 protocols */

#include "qether.h"
#include "hashtable.h"
#include "QuipuServer.h"

#define BUF_SIZE 4096
#define ARP_CACHE_SIZE 1000
#define MAPPED_PORTS 32000


#define ETH_HDRLEN 14      // Ethernet header length
#define IP4_HDRLEN 20      // IPv4 header length
#define ARP_HDRLEN 28      // ARP header length
#define ARPOP_REQUEST 1    // Taken from <linux/if_arp.h>




typedef struct _arp_hdr arp_hdr;
struct _arp_hdr 
{
   
     uint16_t htype;
     uint16_t ptype;
     uint8_t hlen;
     uint8_t plen;
     uint16_t opcode;
     uint8_t sender_mac[6];
     uint8_t sender_ip[4];
     uint8_t target_mac[6];
     uint8_t target_ip[4];
}
;

uint8_t *  allocate_ustrmem (int len);
char *  allocate_sstrmem (int len); 
int send_gatewayarp_request(char interface[],  char target_ip[], char src_ip[]);
int gateway_ipand_iface(in_addr_t * addr, char *interface);

int gateway_ipand_iface(in_addr_t * addr, char *interface)
{
   
       long destination, gateway;
       char iface[IF_NAMESIZE];
       char buf[BUF_SIZE];
       FILE * file;
   
       memset(iface, 0, sizeof(iface));
       memset(buf, 0, sizeof(buf));
   
       file = fopen("/proc/net/route", "r");
       if (!file)
             return -1;
   
       while (fgets(buf, sizeof(buf), file)) 
     {
	
	        if (sscanf(buf, "%s %lx %lx", iface, &destination, &gateway) == 3) 
	  {
	     
	                 if (destination == 0) 
	       {
		   /* default */
		                  *addr = gateway;
		                  strcpy(interface, iface);
		                  fclose(file);
		                  return 0;
	       }
	     
	  }
	
     }
   
   
       /* default route not found */
       if (file)
             fclose(file);
       return -1;
}
       


int send_gatewayarp_request(char interface[],  char target_ip[], char src_ip[])
{
          int status, frame_length, sd, bytes;
          arp_hdr arphdr;
          uint8_t *src_mac, *dst_mac, *ether_frame;
          struct sockaddr_ll device;
          struct ifreq ifr;

          // Allocate memory for various arrays.
          src_mac = allocate_ustrmem (6);
          dst_mac = allocate_ustrmem (6);
          ether_frame = allocate_ustrmem (IP_MAXPACKET);
          //src_ip = allocate_strmem (INET_ADDRSTRLEN);

          // Submit request for a socket descriptor to look up interface.
          if ((sd = socket (AF_INET, SOCK_RAW, IPPROTO_RAW)) < 0) {
            perror ("socket() failed to get raw socket descriptor for using ioctl() ");
            exit (EXIT_FAILURE);
          }


          // Use ioctl() to look up interface name and get its MAC address.
          memset (&ifr, 0, sizeof (ifr));
          snprintf (ifr.ifr_name, sizeof (ifr.ifr_name), "%s", interface);
          if (ioctl (sd, SIOCGIFHWADDR, &ifr) < 0) {
            perror ("ioctl() failed to get source MAC address ");
            return (EXIT_FAILURE);
          }
          close (sd);

          // Copy source MAC address.
          memcpy (src_mac, ifr.ifr_hwaddr.sa_data, 6 * sizeof (uint8_t));
          // copy the mac to the global mac
          //memcpy (source_mac, ifr.ifr_hwaddr.sa_data, 6 * sizeof (uint8_t));
          // Find interface index from interface name and store index in
          // struct sockaddr_ll device, which will be used as an argument of sendto().
          memset (&device, 0, sizeof (device));
          if ((device.sll_ifindex = if_nametoindex (interface)) == 0) {
            perror ("if_nametoindex() failed to obtain interface index ");
            exit (EXIT_FAILURE);
          }

          // Set destination MAC address: broadcast address
          memset (dst_mac, 0xff, 6 * sizeof (uint8_t));


          // Source IP address
          if ((status = inet_pton (AF_INET, src_ip, &arphdr.sender_ip)) != 1) {
            fprintf (stderr, "inet_pton() failed for source IP address.\nError message: %s", strerror (status));
            exit (EXIT_FAILURE);
          }



        // Fill out sockaddr_ll.
         if ((status = inet_pton (AF_INET, target_ip, &arphdr.target_ip)) != 1) {
            fprintf (stderr, "inet_pton() failed for target IP address.\nError message: %s", strerror (status));
            exit (EXIT_FAILURE);
          }
          device.sll_family = AF_PACKET;
          memcpy (device.sll_addr, src_mac, 6 * sizeof (uint8_t));
          device.sll_halen = 6;

          arphdr.htype = htons (1);

          // Protocol type (16 bits): 2048 for IP
          arphdr.ptype = htons (ETH_P_IP);

          // Hardware address length (8 bits): 6 bytes for MAC address
          arphdr.hlen = 6;

          // Protocol address length (8 bits): 4 bytes for IPv4 address
          arphdr.plen = 4;

          // OpCode: 1 for ARP request
          arphdr.opcode = htons (ARPOP_REQUEST);

          // Sender hardware address (48 bits): MAC address
          memcpy (&arphdr.sender_mac, src_mac, 6 * sizeof (uint8_t));


          // Target hardware address (48 bits): zero, since we don't know it yet.
          memset (&arphdr.target_mac, 0, 6 * sizeof (uint8_t));


          // Fill out ethernet frame header.
          // Ethernet frame length = ethernet header (MAC + MAC + ethernet type) + ethernet data (ARP header)
          frame_length = 6 + 6 + 2 + ARP_HDRLEN;

        // Next is ethernet type code (ETH_P_ARP for ARP).
          // http://www.iana.org/assignments/ethernet-numbers
          ether_frame[12] = ETH_P_ARP / 256;
          ether_frame[13] = ETH_P_ARP % 256;

          // Next is ethernet frame data (ARP header).

          // ARP header
          memcpy (ether_frame + ETH_HDRLEN, &arphdr, ARP_HDRLEN * sizeof (uint8_t));

          // Submit request for a raw socket descriptor.
          if ((sd = socket (PF_PACKET, SOCK_RAW, htons (ETH_P_ALL))) < 0) {
            perror ("socket() failed ");
            exit (EXIT_FAILURE);
          }
          printf("Sending Frame .....%d\n",frame_length);

          // Send ethernet frame to socket.
          if ((bytes = sendto (sd, ether_frame, frame_length, 0, (struct sockaddr *) &device, sizeof (device))) <= 0) {
            perror ("sendto() failed");
            exit (EXIT_FAILURE);
          }



          // Close socket descriptor.
          close (sd);
         //print_ethernet_header(ether_frame);
         //  print_ip_header(ether_frame,frame_length);  
        // Free allocated memory.
          free (src_mac);
          free (dst_mac);
          free (ether_frame);


          return (EXIT_SUCCESS);
}


char *
allocate_strmem (int len) {

  void *tmp;

  if (len <= 0) {
    fprintf (stderr, "ERROR: Cannot allocate memory because len = %i in allocate_strmem().\n", len);
    exit (EXIT_FAILURE);
  }

  tmp = (char *) malloc (len * sizeof (char));
  if (tmp != NULL) {
    memset (tmp, 0, len * sizeof (char));
    return (tmp);
  } else
     {

            fprintf (stderr, "ERROR: Cannot allocate memory for array allocate_strmem().\n");
            exit (EXIT_FAILURE);
     }

}



uint8_t *  allocate_ustrmem (int len) {

  void *tmp;

  if (len <= 0) {
    fprintf (stderr, "ERROR: Cannot allocate memory because len = %i in allocate_ustrmem().\n", len);
    exit (EXIT_FAILURE);
  }

  tmp = (uint8_t *) malloc (len * sizeof (uint8_t));
  if (tmp != NULL) {
    memset (tmp, 0, len * sizeof (uint8_t));
    return (tmp);
  } else {
    fprintf (stderr, "ERROR: Cannot allocate memory for array allocate_ustrmem().\n");
    exit (EXIT_FAILURE);
  }
}


JNIEXPORT void JNICALL
Java_com_mmmsys_spider_QuipuServer_print(JNIEnv *env, jobject obj) {

        printf("Hello world!\n");
	//char *src_ip="", *dst_ip="", *mac=""; 

        //send_gatewayarp_request(src_ip, dst_ip, mac);
        return;
}


