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

package com.attn.protocol;

import com.att.arotcpcollector.ip.IPHeader;
import com.att.arotcpcollector.tcp.TCPHeader;

/**
 * Data structure that encapsulate both IPv4Header and TCPHeader
 * @author Borey Sao
 * Date: May 27, 2014
 */
public class Packet {

	private IPHeader ipHeader;
	private TCPHeader tcpHeader;
	private byte[] buffer;

	public IPHeader getIPHeader() {
		return ipHeader;
	}
	public void setIPHeader(IPHeader ipheader) {
		this.ipHeader = ipheader;
	}
	public TCPHeader getTCPHeader() {
		return tcpHeader;
	}
	public void setTCPheader(TCPHeader tcpheader) {
		this.tcpHeader = tcpheader;
	}
	/**
	 * the whole packet data as an array of byte
	 * @return
	 */
	public byte[] getBuffer() {
		return buffer;
	}
	public void setBuffer(byte[] buffer) {
		this.buffer = buffer;
	}
	public int getPacketBodyLength(){
		if(buffer != null){
			int offset = tcpHeader.getTCPHeaderLength() - ipHeader.getIPHeaderLength();
			int len = buffer.length - offset;
			return len;
		}
		return 0;
	}
	/**
	 * get data portion of the packet if available. Otherwise, return empty array of byte
	 * @return array of byte
	 */
	public byte[] getPacketBody(){
		if(buffer != null){
			int offset = tcpHeader.getTCPHeaderLength() - ipHeader.getIPHeaderLength();
			int len = buffer.length - offset;
			if(len > 0){
				byte[] data = new byte[len];
				System.arraycopy(buffer, offset, data, 0, len);
				return data;
			}
		}
		return new byte[0];
	}


}
