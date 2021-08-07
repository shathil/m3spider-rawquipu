package com.mmmsys.spider;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

 

public class QuipuServer {
	
	private ConcurrentLinkedQueue<PacketInfo> deviceToNetworkUDPQueue;
    private ConcurrentLinkedQueue<QuipuRecord> deviceToNetworkTCPQueue;
    private ConcurrentLinkedQueue<QuipuRecord> networkToDeviceQueue;
    private ExecutorService executorService;

    
    private Selector udpSelector;
    private Selector tcpSelector;
    private SocketChannel proxysock;
    private DatagramSocket dagsock;


    public QuipuServer(SocketChannel sock){
    	this.proxysock = sock;
        this.deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
        this.deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
        this.networkToDeviceQueue = new ConcurrentLinkedQueue<>();
    }

    public QuipuServer(DatagramSocket sock){
    	this.dagsock = sock;
        this.deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
        this.deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
        this.networkToDeviceQueue = new ConcurrentLinkedQueue<>();
    }

    /*
    public M3ProxyServer(DatagramSocket sock){
    	this.proxysock = sock;
        this.deviceToNetworkUDPQueue = new ConcurrentLinkedQueue<>();
        this.deviceToNetworkTCPQueue = new ConcurrentLinkedQueue<>();
        this.networkToDeviceQueue = new ConcurrentLinkedQueue<>();
    }*/
     
    public void udpPerclient(){
    	
        try
        {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
            executorService = Executors.newFixedThreadPool(5);
            executorService.submit(new QuipuTunnelRunnable(dagsock, deviceToNetworkUDPQueue, deviceToNetworkTCPQueue,networkToDeviceQueue));
            executorService.submit(new QuipuUDPOutput(deviceToNetworkUDPQueue,udpSelector));
            executorService.submit(new QuipuUDPInput(udpSelector, networkToDeviceQueue));
            executorService.submit(new QuipuTCPInputNet(networkToDeviceQueue, tcpSelector));
            executorService.submit(new QuipuTCPOutputNet(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector));
	}
        catch (IOException e)
        {
            // TODO: Here and elsewhere, we should explicitly notify the user of any errors
            // and suggest that they stop the service, since we can't do it ourselves
            cleanup();
        }

    	
    }
    
    /*
    public void tcpPerclient(){
    	
    	
    	
        try
        {
            udpSelector = Selector.open();
            tcpSelector = Selector.open();
     

            executorService = Executors.newFixedThreadPool(5);
            executorService.execute(new tcpTunnelRunnable(proxysock, deviceToNetworkUDPQueue, deviceToNetworkTCPQueue,networkToDeviceQueue));
            executorService.execute(new M3UDPInput(udpSelector,networkToDeviceQueue));
            executorService.execute(new M3UDPOutput(deviceToNetworkUDPQueue, udpSelector));
            executorService.execute(new M3TCPInput(networkToDeviceQueue, tcpSelector));
            executorService.execute(new M3TCPOutput(deviceToNetworkTCPQueue, networkToDeviceQueue, tcpSelector));
        }
        catch (IOException e)
        {
            cleanup();
        }

    	
    }*/

    private void cleanup()
    {
        deviceToNetworkTCPQueue = null;
        deviceToNetworkUDPQueue = null;
        networkToDeviceQueue = null;
        ByteBufferPool.clear();
        closeResources(udpSelector, tcpSelector);
    }

    
    private static void closeResources(Closeable... resources)
    {
        for (Closeable resource : resources)
        {
            try
            {
                resource.close();
            }
            catch (IOException e)
            {
                // Ignore
            }
        }
    }
    
    
    
    /*
    private static void getTcpSock(int port) throws IOException{
    	
    
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.socket().bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);

        while(true){
            SocketChannel socketChannel = serverSocketChannel.accept();
            if(socketChannel != null){            	
            	socketChannel.configureBlocking(true);
            	socketChannel.socket().setKeepAlive(true);
            	socketChannel.socket().setTcpNoDelay(true);
            	socketChannel.socket().setReceiveBufferSize(65535);
            	socketChannel.socket().setSendBufferSize(65535);;
                //do something with socketChannel...
            	M3ProxyServer clientOne = new M3ProxyServer(socketChannel);
            	clientOne.tcpPerclient();
            }
        }
    }*/
    
    
    private static void startUdpSock(int port) throws IOException{
    	DatagramSocket serverDgramSocket = null;
    	try {
    			serverDgramSocket = new DatagramSocket(port);
    			serverDgramSocket.setReceiveBufferSize(65535);
			serverDgramSocket.setTrafficClass(128);
			serverDgramSocket.setSoTimeout(1);
    			//serverDgramSocket.getChannel().configureBlocking(false);
    	    } catch (SocketException e) {
    	      e.printStackTrace();
    	    }

        QuipuServer clientOne = new QuipuServer(serverDgramSocket);
    	clientOne.udpPerclient();
    	
    }

	public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Syntax: Tunnel Server <file> <port>");
            return;
        }
        
 
        int port = Integer.parseInt(args[0]);
        int type = Integer.parseInt(args[1]);
        startUdpSock(port);
        
        
        
	}    
 
	
}


