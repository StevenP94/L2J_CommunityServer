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
package com.l2jserver.communityserver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javolution.util.FastList;
import javolution.util.FastMap;

import com.l2jserver.communityserver.L2DatabaseFactory;

public class Topic
{
	private static final Logger _log = Logger.getLogger(Topic.class.getName());
	
	// SQL
	private static final String GET_POSTS = "SELECT * FROM posts WHERE serverId=? AND post_forum_id=? AND post_topic_id=?";
	private static final String INSERT_TOPIC = "INSERT INTO topics (serverId,topic_id,topic_forum_id,topic_name,topic_ownerid,topic_permissions) values (?,?,?,?,?,?)";
	private static final String UPDATE_TOPIC = "UPDATE topics SET topic_permissions=? WHERE serverId=? AND topic_id=? AND topic_forum_id=?";
	private static final String DELETE_TOPIC = "DELETE FROM topics WHERE topic_id=? AND topic_forum_id=?";
	
	// type
	public static final int SERVER = 0;
	public static final int INBOX = 1;
	public static final int OUTBOX = 2;
	public static final int ARCHIVE = 3;
	public static final int TEMP_ARCHIVE = 4;
	public static final int MEMO = 5;
	public static final int ANNOUNCE = 6;
	public static final int BULLETIN = 7;
	
	// perm
	public static final int NONE = 0;
	public static final int ALL = 1;
	public static final int READ = 2;
	
	private final int _id; // same as type
	private final int _forumId;
	private final int _sqlDPId;
	private final String _topicName;
	private final int _ownerId;
	private int _lastPostId;
	private int _permissions;
	private final Map<Integer, Post> _posts;
	
	/**
	 * @param ct
	 * @param sqlDPId
	 * @param id
	 * @param fid
	 * @param name
	 * @param oid
	 * @param per
	 */
	public Topic(ConstructorType ct, final int sqlDPId, int id, int fid, String name, int oid, int per)
	{
		_sqlDPId = sqlDPId;
		_id = id;
		_forumId = fid;
		_topicName = name;
		_ownerId = oid;
		_lastPostId = 0;
		_permissions = per;
		_posts = new FastMap<>();
		
		if (ct == ConstructorType.CREATE)
		{
			insertindb();
		}
		else
		{
			loadPosts();
		}
	}
	
	private void loadPosts()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(GET_POSTS))
		{
			statement.setInt(1, _sqlDPId);
			statement.setInt(2, _forumId);
			statement.setInt(3, _id);
			try (ResultSet result = statement.executeQuery())
			{
				while (result.next())
				{
					int postId = Integer.parseInt(result.getString("post_id"));
					int postOwner = Integer.parseInt(result.getString("post_ownerid"));
					String recipientList = result.getString("post_recipient_list");
					long date = Long.parseLong(result.getString("post_date"));
					String title = result.getString("post_title");
					String text = result.getString("post_txt");
					int type = Integer.parseInt(result.getString("post_type"));
					int parentId = Integer.parseInt(result.getString("post_parent_id"));
					int readCount = Integer.parseInt(result.getString("post_read_count"));
					Post p = new Post(ConstructorType.RESTORE, _sqlDPId, postId, postOwner, recipientList, parentId, date, _id, _forumId, title, text, type, readCount);
					_posts.put(postId, p);
					if (postId > _lastPostId)
					{
						_lastPostId = postId;
					}
				}
			}
		}
		catch (Exception e)
		{
			_log.warning("data error on Forum " + _forumId + " : " + e);
			e.printStackTrace();
		}
	}
	
	public int getNewPostId()
	{
		return ++_lastPostId;
	}
	
	public void insertindb()
	{
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(INSERT_TOPIC))
		{
			statement.setInt(1, _sqlDPId);
			statement.setInt(2, _id);
			statement.setInt(3, _forumId);
			statement.setString(4, _topicName);
			statement.setInt(5, _ownerId);
			statement.setInt(6, _permissions);
			statement.execute();
		}
		catch (Exception e)
		{
			_log.warning("error while saving new Topic to db " + e);
		}
	}
	
	public enum ConstructorType
	{
		RESTORE,
		CREATE
	}
	
	public void clearPosts()
	{
		_posts.clear();
	}
	
	public int getPostsSize()
	{
		return _posts.size();
	}
	
	public Post getPost(int j)
	{
		return _posts.get(j);
	}
	
	public void addPost(Post p)
	{
		_posts.put(p.getID(), p);
	}
	
	public void rmPostByID(int id)
	{
		_posts.get(id).deleteme();
		_posts.remove(id);
	}
	
	public Collection<Post> getAllPosts()
	{
		return _posts.values();
	}
	
	public Post[] getLastTwoPosts()
	{
		// if the Topic type is Announce then only Advertise Posts count
		Post[] ret = new Post[2];
		for (Post p : _posts.values())
		{
			if ((_id == ANNOUNCE) && (p.getType() != Post.ADVERTISE))
			{
				continue;
			}
			if ((ret[0] == null) || (ret[0].getDate() < p.getDate()))
			{
				ret[1] = ret[0];
				ret[0] = p;
			}
		}
		return ret;
	}
	
	public FastList<Post> getChildrenPosts(Post parent)
	{
		FastList<Post> ret = new FastList<>();
		if (parent == null)
		{
			return ret;
		}
		// parent post always the first
		ret.add(parent);
		for (Post p : _posts.values())
		{
			if (p.getParentId() == parent.getID())
			{
				ret.add(p);
			}
		}
		return ret;
	}
	
	/**
	 * @return
	 */
	public int getID()
	{
		return _id;
	}
	
	public int getForumID()
	{
		return _forumId;
	}
	
	/**
	 * @return
	 */
	public String getName()
	{
		return _topicName;
	}
	
	/**
	 * @param f
	 */
	public void deleteme(Forum f)
	{
		f.rmTopicByID(getID());
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(DELETE_TOPIC))
		{
			statement.setInt(1, getID());
			statement.setInt(2, f.getID());
			statement.execute();
			statement.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public int getPermissions()
	{
		return _permissions;
	}
	
	public void setPermissions(int val)
	{
		_permissions = val;
		try (Connection con = L2DatabaseFactory.getInstance().getConnection();
			PreparedStatement statement = con.prepareStatement(UPDATE_TOPIC))
		{
			statement.setInt(1, _permissions);
			statement.setInt(2, _sqlDPId);
			statement.setInt(3, _id);
			statement.setInt(4, _forumId);
			statement.execute();
		}
		catch (Exception e)
		{
			_log.warning("error while saving new permissions to db " + e);
		}
	}
}
