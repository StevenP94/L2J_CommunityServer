/*
 * Copyright (C) 2004-2014 L2J Server
 * 
 * This file is part of L2J Server.
 * 
 * L2J Server is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * L2J Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.l2jserver.communityserver.network;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

import javolution.util.FastList;

import org.netcon.NetConnectionConfig;
import org.netcon.NetConnectionListener;

public final class GameServerListener extends NetConnectionListener
{
	private static List<GameServerThread> _gameServers = new FastList<>();
	
	public GameServerListener(final NetConnectionConfig config) throws IOException
	{
		super(config);
		setName(getClass().getSimpleName());
	}
	
	@Override
	protected final void buildTCPNetConnection(final NetConnectionConfig config, final Socket remoteConnection) throws IOException
	{
		final GameServerThread gst = new GameServerThread(config);
		
		gst.connect(remoteConnection);
		gst.start();
		
		_gameServers.add(gst);
	}
}
