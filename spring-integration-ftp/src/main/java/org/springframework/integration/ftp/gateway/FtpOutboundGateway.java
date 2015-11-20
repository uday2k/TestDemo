/*
 * Copyright 2002-2015 the original author or authors.
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

package org.springframework.integration.ftp.gateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.net.ftp.FTPFile;

import org.springframework.integration.file.remote.AbstractFileInfo;
import org.springframework.integration.file.remote.MessageSessionCallback;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.file.remote.gateway.AbstractRemoteFileOutboundGateway;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.ftp.session.FtpFileInfo;

/**
 * Outbound Gateway for performing remote file operations via FTP/FTPS.
 *
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.1
 */
public class FtpOutboundGateway extends AbstractRemoteFileOutboundGateway<FTPFile> {

	/**
	 * Construct an instance using the provided session factory and callback for
	 * performing operations on the session.
	 * @param sessionFactory the session factory.
	 * @param messageSessionCallback the callback.
	 */
	public FtpOutboundGateway(SessionFactory<FTPFile> sessionFactory,
			MessageSessionCallback<FTPFile, ?> messageSessionCallback) {
		super(sessionFactory, messageSessionCallback);
	}

	/**
	 * Construct an instance with the supplied remote file template and callback
	 * for performing operations on the session.
	 * @param remoteFileTemplate the remote file template.
	 * @param messageSessionCallback the callback.
	 */
	public FtpOutboundGateway(RemoteFileTemplate<FTPFile> remoteFileTemplate,
			MessageSessionCallback<FTPFile, ?> messageSessionCallback) {
		super(remoteFileTemplate, messageSessionCallback);
	}

	/**
	 * Construct an instance with the supplied session factory, a command ('ls', 'get'
	 * etc), and an expression to determine the filename.
	 * @param sessionFactory the session factory.
	 * @param command the command.
	 * @param expression the filename expression.
	 */
	public FtpOutboundGateway(SessionFactory<FTPFile> sessionFactory, String command, String expression) {
		super(sessionFactory, command, expression);
	}

	/**
	 * Construct an instance with the supplied remote file template, a command ('ls',
	 * 'get' etc), and an expression to determine the filename.
	 * @param remoteFileTemplate the remote file template.
	 * @param command the command.
	 * @param expression the filename expression.
	 */
	public FtpOutboundGateway(RemoteFileTemplate<FTPFile> remoteFileTemplate, String command, String expression) {
		super(remoteFileTemplate, command, expression);
	}

	@Override
	public String getComponentType() {
		return "ftp:outbound-gateway";
	}

	@Override
	protected boolean isDirectory(FTPFile file) {
		return file.isDirectory();
	}

	@Override
	protected boolean isLink(FTPFile file) {
		return file.isSymbolicLink();
	}

	@Override
	protected String getFilename(FTPFile file) {
		return file.getName();
	}

	@Override
	protected String getFilename(AbstractFileInfo<FTPFile> file) {
		return file.getFilename();
	}

	@Override
	protected long getModified(FTPFile file) {
		return file.getTimestamp().getTimeInMillis();
	}

	@Override
	protected List<AbstractFileInfo<FTPFile>> asFileInfoList(Collection<FTPFile> files) {
		List<AbstractFileInfo<FTPFile>> canonicalFiles = new ArrayList<AbstractFileInfo<FTPFile>>();
		for (FTPFile file : files) {
			canonicalFiles.add(new FtpFileInfo(file));
		}
		return canonicalFiles;
	}

	@Override
	protected FTPFile enhanceNameWithSubDirectory(FTPFile file, String directory) {
		file.setName(directory + file.getName());
		return file;
	}

}
