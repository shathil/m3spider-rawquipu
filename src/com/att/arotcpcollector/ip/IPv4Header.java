/*
 *  Copyright 2014 AT&T
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.att.arotcpcollector.ip;

import java.net.InetAddress;

/**
 * Data structure for IPv4 header as defined in RFC 791.
 * 
 * @author Borey Sao Date: May 8, 2014
 */
public class IPv4Header implements IPHeader {
	//IP packet is the four-bit version field. For IPv4, this has a value of 4 (hence the name IPv4).
	private byte ipVersion;

	//the size of the header (this also coincides with the offset to the data)
	private byte internetHeaderLength;

	//Differentiated Services Code Point (DSCP) => 6 bits
	private byte dscpOrTypeOfService = 0;

	//Explicit Congestion Notification (ECN)
	private byte ecn = 0;

	//The total length in bytes of this IP packet, including the IP header and data (TCP header/UDP + body)
	private int totalLength = 0;

	//primarily used for uniquely identifying the group of fragments of a single IP datagram. 
	private int identification = 0;

	//3 bits field used to control or identify fragments.
	//bit 0: Reserved; must be zero
	//bit 1: Don't Fragment (DF)
	//bit 2: More Fragments (MF)
	private byte flag = 0;
	private boolean mayFragment;
	private boolean lastFragment;

	// The fragment offset for this IP datagram.
	private short fragmentOffset;

	//This field limits a datagram's lifetime
	//It is specified in seconds, but time intervals less than 1 second are rounded up to 1
	private byte timeToLive = 0;

	//TCP or UDP or other
	private byte protocol = 0;

	//for error-checking of the header
	private int headerChecksum = 0;

	private InetAddress sourceIP;

	private InetAddress destinationIP;

	private byte[] optionBytes;

	/**
	 * create a new IPv4 Header
	 * 
	 * @param ipVersion the first header field in an IP packet. It is four-bit. For IPv4, this has a value of 4.
	 * @param internetHeaderLength the second field (four bits) is the IP header length (from 20 to 60 bytes)
	 * @param dscpOrTypeOfService type of service
	 * @param ecn Explicit Congestion Notification
	 * @param totalLength total length of this packet including header and body in bytes (max 35535).
	 * @param idenfication primarily used for uniquely identifying the group of fragments of a single IP datagram
	 * @param mayFragment bit number 1 of Flag. For DF (Don't Fragment)
	 * @param lastFragment bit number 2 of Flag. For MF (More Fragment) 
	 * @param fragmentOffset 13 bits long and specifies the offset of a particular fragment relative to the beginning of 
	 * the original unfragmented IP datagram.
	 * @param timeToLive 8 bits field for preventing datagrams from persisting.
	 * @param protocol defines the protocol used in the data portion of the IP datagram
	 * @param headerChecksum 16-bits field used for error-checking of the header
	 * @param sourceIP IPv4 address of sender.
	 * @param destinationIP IPv4 address of receiver.
	 * @param optionBytes optional field.
	 */
	public IPv4Header(byte ipVersion, byte internetHeaderLength, byte dscpOrTypeOfService, byte ecn, 
			int totalLength, int idenfication, boolean mayFragment, boolean lastFragment,short fragmentOffset,
					  byte timeToLive, byte protocol, int headerChecksum, InetAddress sourceIP, InetAddress destinationIP, byte[] optionBytes) {
		
		this.ipVersion = ipVersion;
		this.internetHeaderLength = internetHeaderLength;
		this.dscpOrTypeOfService = dscpOrTypeOfService;
		this.ecn = ecn;
		this.totalLength = totalLength;
		this.identification = idenfication;
		this.mayFragment = mayFragment;
		if (mayFragment) {
			this.flag |= 0x40;
		}
		this.lastFragment = lastFragment;
		if (lastFragment) {
			this.flag |= 0x20;
		}
		this.fragmentOffset = fragmentOffset;
		this.timeToLive = timeToLive;
		this.protocol = protocol;
		this.headerChecksum = headerChecksum;
		this.sourceIP = sourceIP;
		this.destinationIP = destinationIP;
		this.optionBytes = optionBytes;
	}

	public IPv4Header(IPv4Header header) {
		this(
				header.getIpVersion(), header.getInternetHeaderLength(), header.getDscpOrTypeOfService(), header.getEcn(),
				header.getTotalLength(), header.getIdentification(), header.isMayFragment(), header.isLastFragment(),
				header.getFragmentOffset(), header.getTimeToLive(), header.getProtocol(), header.getHeaderChecksum(),
				header.getSourceIP(), header.getDestinationIP(), header.getOptionBytes()
		);
	}

	@Override
	public byte getIpVersion() {
		return ipVersion;
	}

	public byte getInternetHeaderLength() {
		return internetHeaderLength;
	}

	public byte getDscpOrTypeOfService() {
		return dscpOrTypeOfService;
	}

	public byte getEcn() {
		return ecn;
	}

	/**
	 * total length of this packet in bytes including IP Header and body(TCP/UDP
	 * header + data)
	 * 
	 * @return
	 */
	public int getTotalLength() {
		return totalLength;
	}

	/**
	 * total length of IP header in bytes.
	 * 
	 * @return
	 */
	public int getIPHeaderLength() {
		return (this.internetHeaderLength * 4);
	}

	public int getIdentification() {
		return identification;
	}

	public byte getFlag() {
		return flag;
	}

	public boolean isMayFragment() {
		return mayFragment;
	}

	public boolean isLastFragment() {
		return lastFragment;
	}

	public short getFragmentOffset() {
		return fragmentOffset;
	}

	public byte getTimeToLive() {
		return timeToLive;
	}

	@Override
	public byte getProtocol() {
		return protocol;
	}

	public int getHeaderChecksum() {
		return headerChecksum;
	}

	@Override
	public InetAddress getSourceIP() {
		return sourceIP;
	}

	@Override
	public InetAddress getDestinationIP() {
		return destinationIP;
	}

	public byte[] getOptionBytes() {
		return optionBytes;
	}

	public void setInternetHeaderLength(byte internetHeaderLength) {
		this.internetHeaderLength = internetHeaderLength;
	}

	public void setDscpOrTypeOfService(byte dscpOrTypeOfService) {
		this.dscpOrTypeOfService = dscpOrTypeOfService;
	}

	public void setEcn(byte ecn) {
		this.ecn = ecn;
	}

	@Override
	public void setTotalLength(int totalLength) {
		this.totalLength = totalLength;
	}

	public void setIdentification(int identification) {
		this.identification = identification;
	}

	public void setFlag(byte flag) {
		this.flag = flag;
	}

	public void setMayFragment(boolean mayFragment) {
		this.mayFragment = mayFragment;
		if (mayFragment) {
			this.flag |= 0x40;
		} else {
			this.flag &= 0xBF;
		}
	}

	public void setLastFragment(boolean lastFragment) {
		this.lastFragment = lastFragment;
		if (lastFragment) {
			this.flag |= 0x20;
		} else {
			this.flag &= 0xDF;
		}
	}

	public void setFragmentOffset(short fragmentOffset) {
		this.fragmentOffset = fragmentOffset;
	}

	public void setTimeToLive(byte timeToLive) {
		this.timeToLive = timeToLive;
	}

	public void setProtocol(byte protocol) {
		this.protocol = protocol;
	}

	public void setHeaderChecksum(int headerChecksum) {
		this.headerChecksum = headerChecksum;
	}

	@Override
	public void setSourceIP(InetAddress sourceIP) {
		this.sourceIP = sourceIP;
	}

	@Override
	public void setDestinationIP(InetAddress destinationIP) {
		this.destinationIP = destinationIP;
	}

	public void setOptionBytes(byte[] optionBytes) {
		this.optionBytes = optionBytes;
	}

}
