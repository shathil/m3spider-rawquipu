package com.mmmsys.spider;

/*
** Copyright 2015, Mohamed Naufal
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/


// This is a good design. But we need an alternative design where we can track 
//the original IP addrerss through the inetSockAddress. 


import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class QuipuUDPOutput implements Runnable
{
    private static final String TAG = QuipuUDPOutput.class.getSimpleName();

    private ConcurrentLinkedQueue<PacketInfo> inputQueue;
    private Selector selector;
    private static final int HEADER_SIZE = PacketInfo.IP4_HEADER_SIZE+PacketInfo.UDP_HEADER_SIZE;
    private static final int MAX_CACHE_SIZE = 1000;
    private LRUCache<String, DatagramChannel> channelCache =
            new LRUCache<>(MAX_CACHE_SIZE, new LRUCache.CleanupCallback<String, DatagramChannel>()
            {
                @Override
                public void cleanup(Map.Entry<String, DatagramChannel> eldest)
                {
                    closeChannel(eldest.getValue());
                }
            });

    public QuipuUDPOutput(ConcurrentLinkedQueue<PacketInfo> inputQueue, Selector selector)
    {
        this.inputQueue = inputQueue;
        this.selector = selector;
    }

    @Override
    public void run()
    {
       	System.out.println(TAG + " Started");
        try
        {

            Thread currentThread = Thread.currentThread();
            while (true)
            {
                PacketInfo currentPacket;
                // TODO: Block when not connected
                do
                {
                    currentPacket = inputQueue.poll();
                    if (currentPacket != null)
                        break;
                    Thread.sleep(10);
                } while (!currentThread.isInterrupted());

                if (currentThread.isInterrupted())
                    break;

                InetAddress destinationAddress = currentPacket.ip4Header.destinationAddress;
                int destinationPort = currentPacket.udpHeader.destinationPort;
                int sourcePort = currentPacket.udpHeader.sourcePort;

                String ipAndPort = destinationAddress.getHostAddress() + ":" + destinationPort + ":" + sourcePort;
                DatagramChannel outputChannel = channelCache.get(ipAndPort);
                if (outputChannel == null) {
                    outputChannel = DatagramChannel.open();
                    try
                    {
                        outputChannel.connect(new InetSocketAddress(destinationAddress, destinationPort));
                    }
                    catch (IOException e)
                    {
                        System.out.println("Connection error: " + ipAndPort+ e.toString());
                        closeChannel(outputChannel);
                        ByteBufferPool.release(currentPacket.backingBuffer);
                        continue;
                    }
                    outputChannel.configureBlocking(false);
                    outputChannel.socket().setSendBufferSize(65535);
                    outputChannel.socket().setReceiveBufferSize(65535);
                    //System.out.println(TAG + "Packet Status "+ currentPacket.backingBuffer.toString()+" bytes ");
                    
                    
                    //currentPacket.swapSourceAndDestination();
                    
                    QuipuRecord qRecord = new QuipuRecord(currentPacket.getClientAddress(), currentPacket.backingBuffer);
                    selector.wakeup();
                    outputChannel.register(selector, SelectionKey.OP_READ, qRecord);
                    channelCache.put(ipAndPort, outputChannel);
                }

                try
                {
                	
                    currentPacket.backingBuffer.position(HEADER_SIZE);
                    currentPacket.backingBuffer.limit(currentPacket.ip4Header.totalLength);

                    int nb = outputChannel.write(currentPacket.backingBuffer);   
                    InetSocketAddress cadress = (InetSocketAddress)currentPacket.getClientAddress();
                    System.out.println(TAG + cadress.getHostString() +"Packet written to socket "+ currentPacket.backingBuffer.toString()+" "+nb+" bytes ");

                }
                catch (IOException e)
                {
                    System.out.println("Network write error: " + ipAndPort+e.toString());
                    channelCache.remove(ipAndPort);
                    closeChannel(outputChannel);
                }
               ByteBufferPool.release(currentPacket.backingBuffer);
            }
        }
        catch (InterruptedException e)
        {
        	System.out.println("Stopping "+e.toString());
        }
        catch (IOException e)
        {
        	System.out.println(e.toString());
        }
        finally
        {
            closeAll();
        }
    }

    private void closeAll()
    {
        Iterator<Map.Entry<String, DatagramChannel>> it = channelCache.entrySet().iterator();
        while (it.hasNext())
        {
            closeChannel(it.next().getValue());
            it.remove();
        }
    }

    private void closeChannel(DatagramChannel channel)
    {
        try
        {
            channel.close();
        }
        catch (IOException e)
        {
            // Ignore
        }
    }
}
