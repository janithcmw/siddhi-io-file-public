/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.io.file;

import org.apache.log4j.Logger;
import org.wso2.carbon.messaging.ServerConnector;
import org.wso2.carbon.messaging.exceptions.ClientConnectorException;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;
import org.wso2.extension.siddhi.io.file.listeners.FileSystemListener;
import org.wso2.extension.siddhi.io.file.processors.FileProcessor;
import org.wso2.extension.siddhi.io.file.util.Constants;
import org.wso2.extension.siddhi.io.file.util.FileSourceConfiguration;
import org.wso2.extension.siddhi.io.file.util.FileSourceServiceProvider;
import org.wso2.extension.siddhi.io.file.util.VFSClientConnectorCallback;
import org.wso2.siddhi.annotation.Example;
import org.wso2.siddhi.annotation.Extension;
import org.wso2.siddhi.annotation.Parameter;
import org.wso2.siddhi.annotation.util.DataType;
import org.wso2.siddhi.core.config.SiddhiAppContext;
import org.wso2.siddhi.core.exception.ConnectionUnavailableException;
import org.wso2.siddhi.core.exception.SiddhiAppCreationException;
import org.wso2.siddhi.core.exception.SiddhiAppRuntimeException;
import org.wso2.siddhi.core.stream.input.source.Source;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import org.wso2.siddhi.core.util.config.ConfigReader;
import org.wso2.siddhi.core.util.transport.OptionHolder;
import org.wso2.transport.file.connector.sender.VFSClientConnector;
import org.wso2.transport.file.connector.server.FileServerConnector;
import org.wso2.transport.file.connector.server.FileServerConnectorProvider;
import org.wso2.transport.remotefilesystem.RemoteFileSystemConnectorFactory;
import org.wso2.transport.remotefilesystem.exception.RemoteFileSystemConnectorException;
import org.wso2.transport.remotefilesystem.server.connector.contract.RemoteFileSystemServerConnector;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Implementation of siddhi-io-file source.
 * */
@Extension(
        name = "file",
        namespace = "source",
        description = "" +
                "File Source provides the functionality for user to feed data to siddhi from " +
                "files. Both text and binary files are supported by file source.",
        parameters = {
                @Parameter(
                        name = "dir.uri",
                        description =
                                "Used to specify a directory to be processed. \n" +
                                "All the files inside this directory will be processed. \n" +
                                "Only one of 'dir.uri' and 'file.uri' should be provided.\n" +
                                "This uri MUST have the respective protocol specified.",
                        type = {DataType.STRING}
                ),

                @Parameter(
                        name = "file.uri",
                        description =
                                "Used to specify a file to be processed. \n" +
                                        " Only one of 'dir.uri' and 'file.uri' should be provided.\n" +
                                        "This uri MUST have the respective protocol specified.\n",
                        type = {DataType.STRING}
                ),

                @Parameter(
                        name = "mode",
                        description =
                                "This parameter is used to specify how files in given directory should." +
                                "Possible values for this parameter are,\n" +
                                        "1. TEXT.FULL : to read a text file completely at once.\n" +
                                        "2. BINARY.FULL : to read a binary file completely at once.\n" +
                                        "3. LINE : to read a text file line by line.\n" +
                                        "4. REGEX : to read a text file and extract data using a regex.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "line"
                ),

                @Parameter(
                        name = "tailing",
                        description = "" +
                                "This can either have value true or false. By default it will be true. \n" +
                                "This attribute allows user to specify whether the file should be tailed or not. \n" +
                                "If tailing is enabled, the first file of the directory will be tailed.\n" +
                                "Also tailing should not be enabled in 'binary.full' or 'text.full' modes.\n",
                        type = {DataType.BOOL},
                        optional = true,
                        defaultValue = "true"
                ),

                @Parameter(
                        name = "action.after.process",
                        description = "" +
                                "This parameter is used to specify the action which should be carried out \n" +
                                "after processing a file in the given directory. \n" +
                                "It can be either DELETE or MOVE and default value will be 'DELETE'.\n" +
                                "If the action.after.process is MOVE, user must specify the location to " +
                                "move consumed files using 'move.after.process' parameter.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "delete"
                ),

                @Parameter(
                        name = "action.after.failure",
                        description = "" +
                                "This parameter is used to specify the action which should be carried out " +
                                "if a failure occurred during the process. \n" +
                                "It can be either DELETE or MOVE and default value will be 'DELETE'.\n" +
                                "If the action.after.failure is MOVE, user must specify the location to " +
                                "move consumed files using 'move.after.failure' parameter.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "delete"
                ),

                @Parameter(
                        name = "move.after.process",
                        description = "" +
                                "If action.after.process is MOVE, user must specify the location to " +
                                "move consumed files using 'move.after.process' parameter.\n" +
                                "This should be the absolute path of the file that going to be created after moving " +
                                "is done.\n" +
                                "This uri MUST have the respective protocol specified.\n",
                        type = {DataType.STRING}
                ),

                @Parameter(
                        name = "move.after.failure",
                        description = "" +
                                "If action.after.failure is MOVE, user must specify the location to " +
                                "move consumed files using 'move.after.failure' parameter.\n" +
                                "This should be the absolute path of the file that going to be created after moving " +
                                "is done.\n" +
                                "This uri MUST have the respective protocol specified.\n",
                        type = {DataType.STRING}
                ),

                @Parameter(
                        name = "begin.regex",
                        description = "" +
                                "This will define the regex to be matched at the beginning of the " +
                                "retrieved content.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "None"
                ),

                @Parameter(
                        name = "end.regex",
                        description = "" +
                                "This will define the regex to be matched at the end of the " +
                                "retrieved content.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "None"
                ),

                @Parameter(
                        name = "file.polling.interval",
                        description = "" +
                                "This parameter is used to specify the time period (in milliseconds) " +
                                "of a polling cycle for a file.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "1000"
                ),

                @Parameter(
                        name = "dir.polling.interval",
                        description = "This parameter is used to specify the time period (in milliseconds) " +
                                "of a polling cycle for a directory.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "1000"
                ),

                @Parameter(
                        name = "timeout",
                        description = "This parameter is used to specify the maximum time period (in milliseconds) " +
                                " for waiting until a file is processed.\n",
                        type = {DataType.STRING},
                        optional = true,
                        defaultValue = "5000"
                ),
        },
        examples = {
                @Example(
                        syntax = "" +
                                "@source(type='file',\n" +
                                "mode='text.full',\n" +
                                "tailing='false'\n " +
                                "dir.uri='file://abc/xyz',\n" +
                                "action.after.process='delete',\n" +
                                "@map(type='json')) \n" +
                                "define stream FooStream (symbol string, price float, volume long); \n",

                        description = "" +
                                "Under above configuration, all the files in directory will be picked and read " +
                                "one by one.\n" +
                                "In this case, it's assumed that all the files contains json valid json strings with " +
                                "keys 'symbol','price' and 'volume'.\n" +
                                "Once a file is read, " +
                                "its content will be converted to an event using siddhi-map-json " +
                                "extension and then, that event will be received to the FooStream.\n" +
                                "Finally, after reading is finished, the file will be deleted.\n"
                ),

                @Example(
                        syntax = "" +
                                "@source(type='file',\n" +
                                "mode='files.repo.line',\n" +
                                "tailing='true',\n" +
                                "dir.uri='file://abc/xyz',\n" +
                                "@map(type='json')) \n" +
                                "define stream FooStream (symbol string, price float, volume long);\n ",

                        description = "" +
                                "Under above configuration, " +
                                "the first file in directory '/abc/xyz'  will be picked and read " +
                                "line by line.\n" +
                                "In this case, it is assumed that the file contains lines json strings.\n" +
                                "For each line, line content will be converted to an event using siddhi-map-json " +
                                "extension and then, that event will be received to the FooStream.\n" +
                                "Once file content is completely read, " +
                                "it will keep checking whether a new entry is added to the file or not.\n" +
                                "If such entry is added, it will be immediately picked up and processed.\n"
                )
        }
)
public class FileSource extends Source {
    private static final Logger log = Logger.getLogger(FileSource.class);

    private SourceEventListener sourceEventListener;
    private FileSourceConfiguration fileSourceConfiguration;
    private RemoteFileSystemConnectorFactory fileSystemConnectorFactory;
    private FileSourceServiceProvider fileSourceServiceProvider;
    private RemoteFileSystemServerConnector fileSystemServerConnector;
    private String filePointer = "0";
    private String[] requiredProperties;
    private boolean isTailingEnabled = true;
    private SiddhiAppContext siddhiAppContext;

    private String mode;
    private String actionAfterProcess;
    private String actionAfterFailure = null;
    private String moveAfterProcess;
    private String moveAfterFailure = null;
    private String tailing;
    private String beginRegex;
    private String endRegex;
    private String tailedFileURI;
    private String dirUri;
    private String fileUri;
    private String dirPollingInterval;
    private String filePollingInterval;

    private long timeout = 5000;

    @Override
    public void init(SourceEventListener sourceEventListener, OptionHolder optionHolder, String[] requiredProperties,
                     ConfigReader configReader, SiddhiAppContext siddhiAppContext) {
        this.sourceEventListener = sourceEventListener;
        this.siddhiAppContext = siddhiAppContext;
        this.requiredProperties = requiredProperties.clone();
        this.fileSourceConfiguration = new FileSourceConfiguration();

        this.fileSourceServiceProvider = FileSourceServiceProvider.getInstance();
        this.fileSystemConnectorFactory = fileSourceServiceProvider.getFileSystemConnectorFactory();
        if (optionHolder.isOptionExists(Constants.DIR_URI)) {
            dirUri = optionHolder.validateAndGetStaticValue(Constants.DIR_URI);
            validateURL(dirUri, "dir.uri");
        }
        if (optionHolder.isOptionExists(Constants.FILE_URI)) {
            fileUri = optionHolder.validateAndGetStaticValue(Constants.FILE_URI);
            validateURL(fileUri, "file.uri");
        }

        if (dirUri != null && fileUri != null) {
            throw new SiddhiAppCreationException("Only one of directory uri or file uri should be provided. But both " +
                    "have been provided.");
        }
        if (dirUri == null && fileUri == null) {
            throw new SiddhiAppCreationException("Either directory uri or file uri must be provided. But none of them" +
                    "found.");
        }

        mode = optionHolder.validateAndGetStaticValue(Constants.MODE, Constants.LINE);

        if (Constants.TEXT_FULL.equalsIgnoreCase(mode) || Constants.BINARY_FULL.equalsIgnoreCase(mode)) {
            tailing = optionHolder.validateAndGetStaticValue(Constants.TAILING, Constants.FALSE);
        } else {
            tailing = optionHolder.validateAndGetStaticValue(Constants.TAILING, Constants.TRUE);
        }
        isTailingEnabled = Boolean.parseBoolean(tailing);

        if (isTailingEnabled) {
            actionAfterProcess = optionHolder.validateAndGetStaticValue(Constants.ACTION_AFTER_PROCESS,
                    Constants.NONE);
        } else {
            actionAfterProcess = optionHolder.validateAndGetStaticValue(Constants.ACTION_AFTER_PROCESS,
                    Constants.DELETE);
        }
        actionAfterFailure = optionHolder.validateAndGetStaticValue(Constants.ACTION_AFTER_FAILURE, Constants.DELETE);
        // TODO : When file.uri has been provided, the file uri should be provided for move.after.process parameter.
        // TODO : Fix this in carbon transport
        if (optionHolder.isOptionExists(Constants.MOVE_AFTER_PROCESS)) {
            moveAfterProcess = optionHolder.validateAndGetStaticValue(Constants.MOVE_AFTER_PROCESS);
            validateURL(moveAfterProcess, "moveAfterProcess");
        }
        if (optionHolder.isOptionExists(Constants.MOVE_AFTER_FAILURE)) {
            moveAfterFailure = optionHolder.validateAndGetStaticValue(Constants.MOVE_AFTER_FAILURE);
            validateURL(moveAfterFailure, "moveAfterFailure");
        }

        dirPollingInterval = optionHolder.validateAndGetStaticValue(Constants.DIRECTORY_POLLING_INTERVAL, "1000");

        filePollingInterval = optionHolder.validateAndGetStaticValue(Constants.FILE_POLLING_INTERVAL, "1000");

        String timeoutValue = optionHolder.validateAndGetStaticValue(Constants.TIMEOUT, "5000");
        try {
            timeout = Long.parseLong(timeoutValue);
        } catch (NumberFormatException e) {
            throw new SiddhiAppRuntimeException("Value provided for timeout, " + timeoutValue + " is invalid.", e);
        }
        beginRegex = optionHolder.validateAndGetStaticValue(Constants.BEGIN_REGEX, null);
        endRegex = optionHolder.validateAndGetStaticValue(Constants.END_REGEX, null);

        validateParameters();
        createInitialSourceConf();
        updateSourceConf();
        getPattern();

        siddhiAppContext.getSnapshotService().addSnapshotable("siddhi-io-file", this);
    }


    @Override
    public Class[] getOutputEventClasses() {
        return new Class[]{String.class, byte[].class};
    }

    @Override
    public void connect(ConnectionCallback connectionCallback) throws ConnectionUnavailableException {
        updateSourceConf();
        deployServers();
    }

    @Override
    public void disconnect() {
        try {
            if (fileSystemServerConnector != null) {
                fileSystemServerConnector.stop();
                fileSystemServerConnector = null;
            }
            if (isTailingEnabled) {
                fileSourceConfiguration.getFileServerConnector().stop();
                fileSourceConfiguration.setFileServerConnector(null);
            }
            ExecutorService executorService = fileSourceConfiguration.getExecutorService();
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
        } catch (ServerConnectorException e) {
           throw new SiddhiAppRuntimeException("Failed to stop the file server when shutting down the siddhi app '" +
                   siddhiAppContext.getName() + "' due to " + e.getMessage(), e);
        }
    }


    public void destroy() {

    }

    public void pause() {
        try {
            if (fileSystemServerConnector != null) {
                fileSystemServerConnector.stop();
            }
            if (isTailingEnabled && fileSourceConfiguration.getFileServerConnector() != null) {
                fileSourceConfiguration.getFileServerConnector().stop();
            }
        } catch (ServerConnectorException e) {
            throw new SiddhiAppRuntimeException("Failed to stop the file server.", e);
        }
    }

    public void resume() {
        try {
            updateSourceConf();
            deployServers();
        } catch (ConnectionUnavailableException e) {
            throw new SiddhiAppRuntimeException("Failed to resume siddhi app runtime.", e);
        }
    }

    public Map<String, Object> currentState() {
        Map<String, Object> currentState = new HashMap<>();
        currentState.put(Constants.FILE_POINTER, fileSourceConfiguration.getFilePointer());
        currentState.put(Constants.TAILED_FILE, fileSourceConfiguration.getTailedFileURI());
        currentState.put(Constants.TAILING_REGEX_STRING_BUILDER,
                fileSourceConfiguration.getTailingRegexStringBuilder());
        return currentState;
    }

    public void restoreState(Map<String, Object> map) {
        this.filePointer = map.get(Constants.FILE_POINTER).toString();
        this.tailedFileURI = map.get(Constants.TAILED_FILE).toString();
        fileSourceConfiguration.setFilePointer(filePointer);
        fileSourceConfiguration.setTailedFileURI(tailedFileURI);
        fileSourceConfiguration.updateTailingRegexStringBuilder(
                (StringBuilder) map.get(Constants.TAILING_REGEX_STRING_BUILDER));
    }

    private void createInitialSourceConf() {
        fileSourceConfiguration.setBeginRegex(beginRegex);
        fileSourceConfiguration.setEndRegex(endRegex);
        fileSourceConfiguration.setMode(mode);
        fileSourceConfiguration.setTailingEnabled(Boolean.parseBoolean(tailing));
        fileSourceConfiguration.setFilePollingInterval(filePollingInterval);
        fileSourceConfiguration.setRequiredProperties(requiredProperties);
        fileSourceConfiguration.setActionAfterProcess(actionAfterProcess);
        fileSourceConfiguration.setMoveAfterProcess(moveAfterProcess);
        fileSourceConfiguration.setTimeout(timeout);
    }

    private void updateSourceConf() {
        fileSourceConfiguration.setFilePointer(filePointer);
        fileSourceConfiguration.setTailedFileURI(tailedFileURI);
    }

    private Map<String, String> getFileSystemServerProperties() {
        Map<String, String> map = new HashMap<>();

        map.put(Constants.TRANSPORT_FILE_DIR_URI, dirUri);
        if (actionAfterProcess != null) {
            map.put(Constants.ACTION_AFTER_PROCESS_KEY, actionAfterProcess.toUpperCase(Locale.ENGLISH));
        }
        map.put(Constants.MOVE_AFTER_PROCESS_KEY.toUpperCase(Locale.ENGLISH), moveAfterProcess);
        map.put(Constants.POLLING_INTERVAL, dirPollingInterval);
        map.put(Constants.FILE_SORT_ATTRIBUTE, Constants.NAME);
        map.put(Constants.FILE_SORT_ASCENDING, Constants.TRUE.toUpperCase(Locale.ENGLISH));
        map.put(Constants.CREATE_MOVE_DIR, Constants.TRUE.toUpperCase(Locale.ENGLISH));
        map.put(Constants.ACK_TIME_OUT, "5000");

        if (Constants.BINARY_FULL.equalsIgnoreCase(mode) ||
                Constants.TEXT_FULL.equalsIgnoreCase(mode)) {
            map.put(Constants.READ_FILE_FROM_BEGINNING, Constants.TRUE.toUpperCase(Locale.ENGLISH));
        } else {
            map.put(Constants.READ_FILE_FROM_BEGINNING, Constants.FALSE.toUpperCase(Locale.ENGLISH));
        }
        if (actionAfterFailure != null) {
            map.put(Constants.ACTION_AFTER_FAILURE_KEY, actionAfterFailure.toUpperCase(Locale.ENGLISH));
        }
        if (moveAfterFailure != null) {
            map.put(Constants.MOVE_AFTER_FAILURE_KEY, moveAfterFailure.toUpperCase(Locale.ENGLISH));
        }
        return map;
    }

    private void validateParameters() {
        if (Constants.TEXT_FULL.equalsIgnoreCase(mode) || Constants.BINARY_FULL.equalsIgnoreCase(mode)) {
            if (isTailingEnabled) {
                throw new SiddhiAppCreationException("Tailing has been enabled by user or by default." +
                        "But tailing can't be enabled in '" + mode + "' mode.");
            }

            if (Constants.BINARY_FULL.equalsIgnoreCase(mode)) {
                if (beginRegex != null && endRegex != null) {
                    throw new SiddhiAppCreationException("'begin.regex' and 'end.regex' can be only provided if the" +
                            " mode is 'regex'. But provided mode is '" + mode + "'.");
                }
            }
        }

        if (isTailingEnabled && moveAfterProcess != null) {
            throw new SiddhiAppCreationException("Tailing has been enabled by user or by default." +
                    "'moveAfterProcess' cannot be used when tailing is enabled. " +
                    "Hence stopping the SiddhiApp. ");
        }

        if (Constants.DELETE.equalsIgnoreCase(actionAfterProcess) && moveAfterProcess != null) {
            throw new SiddhiAppCreationException("'moveAfterProcess' can only be used when " +
                    "'action.after.process' is 'move'. But it has been used when 'action.after.process' is 'delete'." +
                    "Hence stopping the SiddhiApp. ");
        }

        if (Constants.MOVE.equalsIgnoreCase(actionAfterProcess) && (moveAfterProcess == null)) {
            throw new SiddhiAppCreationException("'moveAfterProcess' has not been provided where it is mandatory when" +
                    " 'actionAfterProcess' is 'move'. Hence stopping the SiddhiApp. ");
        }

        if (Constants.REGEX.equalsIgnoreCase(mode)) {
            if (beginRegex == null && endRegex == null) {
                mode = Constants.LINE;
            }
        }
    }

    private void deployServers() throws ConnectionUnavailableException {
        ExecutorService executorService = siddhiAppContext.getExecutorService();
        createInitialSourceConf();
        fileSourceConfiguration.setExecutorService(executorService);

        if (dirUri != null) {
            Map<String, String> properties = getFileSystemServerProperties();
            FileSystemListener fileSystemListener = new FileSystemListener(sourceEventListener,
                    fileSourceConfiguration);
            try {
                fileSystemServerConnector =  fileSystemConnectorFactory.createServerConnector(
                        siddhiAppContext.getName(), properties, fileSystemListener);
                fileSourceConfiguration.setFileSystemServerConnector(fileSystemServerConnector);
                fileSystemServerConnector.start();
            } catch (RemoteFileSystemConnectorException e) {
                throw new ConnectionUnavailableException("Failed to connect to the remote file system server. ", e);
            }
        } else if (fileUri != null) {
            Map<String, String> properties = new HashMap<>();
            properties.put(Constants.ACTION, Constants.READ);
            properties.put(Constants.MAX_LINES_PER_POLL, "10");
            properties.put(Constants.POLLING_INTERVAL, filePollingInterval);
            if (actionAfterFailure != null) {
                properties.put(Constants.ACTION_AFTER_FAILURE_KEY, actionAfterFailure);
            }
            if (moveAfterFailure != null) {
                properties.put(Constants.MOVE_AFTER_FAILURE_KEY, moveAfterFailure);
            }

            if (fileSourceConfiguration.isTailingEnabled()) {
                if (fileSourceConfiguration.getTailedFileURI() == null) {
                    fileSourceConfiguration.setTailedFileURI(fileUri);
                }

                if (fileSourceConfiguration.getTailedFileURI().equalsIgnoreCase(fileUri)) {
                    properties.put(Constants.START_POSITION, fileSourceConfiguration.getFilePointer());
                    properties.put(Constants.PATH, fileUri);

                    FileServerConnectorProvider fileServerConnectorProvider =
                            fileSourceServiceProvider.getFileServerConnectorProvider();
                    FileProcessor fileProcessor = new FileProcessor(sourceEventListener,
                            fileSourceConfiguration);
                    final ServerConnector fileServerConnector = fileServerConnectorProvider
                            .createConnector("file-server-connector", properties);
                    fileServerConnector.setMessageProcessor(fileProcessor);
                    fileSourceConfiguration.setFileServerConnector((FileServerConnector) fileServerConnector);

                    Runnable runnableServer = () -> {
                        try {
                            fileServerConnector.start();
                        } catch (ServerConnectorException e) {
                            log.error(String.format("Failed to start the server for file '%s'. " +
                                    "Hence starting to process next file.", fileUri));
                        }
                    };
                    fileSourceConfiguration.getExecutorService().execute(runnableServer);
                }
            } else {
                properties.put(Constants.URI, fileUri);
                properties.put(Constants.ACK_TIME_OUT, "1000");
                VFSClientConnector vfsClientConnector = new VFSClientConnector();
                FileProcessor fileProcessor = new FileProcessor(sourceEventListener, fileSourceConfiguration);
                vfsClientConnector.setMessageProcessor(fileProcessor);
                VFSClientConnectorCallback vfsClientConnectorCallback = new VFSClientConnectorCallback();

                Runnable runnableClient = () -> {
                    try {
                        vfsClientConnector.send(null, vfsClientConnectorCallback, properties);
                        vfsClientConnectorCallback.waitTillDone(timeout, fileUri);
                        if (actionAfterProcess != null) {
                            properties.put(Constants.URI, fileUri);
                            properties.put(Constants.ACTION, actionAfterProcess);
                            if (moveAfterProcess != null) {
                                properties.put(Constants.DESTINATION, moveAfterProcess);
                            }
                            vfsClientConnector.send(null, vfsClientConnectorCallback, properties);
                            vfsClientConnectorCallback.waitTillDone(timeout, fileUri);
                        }
                    } catch (ClientConnectorException e) {
                        log.error(String.format("Failure occurred in vfs-client while reading the file '%s'.",
                                fileUri), e);
                    } catch (InterruptedException e) {
                        log.error(String.format("Failed to get callback from vfs-client  for file '%s'.", fileUri), e);
                    }
                };
                fileSourceConfiguration.getExecutorService().execute(runnableClient);
            }
        }
    }

    private void getPattern() {
        String beginRegex = fileSourceConfiguration.getBeginRegex();
        String endRegex = fileSourceConfiguration.getEndRegex();
        Pattern pattern;
        try {
            if (beginRegex != null && endRegex != null) {
                pattern = Pattern.compile(beginRegex + "((.|\n)*?)" + endRegex);
            } else if (beginRegex != null) {
                pattern = Pattern.compile(beginRegex + "((.|\n)*?)" + beginRegex);
            } else if (endRegex != null) {
                pattern = Pattern.compile("((.|\n)*?)(" + endRegex + ")");
            } else {
                pattern = Pattern.compile("(\n$)"); // this will not be reached
            }
        } catch (PatternSyntaxException e) {
            throw new SiddhiAppCreationException("Cannot compile the regex '" + beginRegex +
                    "' and '" + endRegex + "'. Hence shutting down the siddhiApp. ");
        }
        fileSourceConfiguration.setPattern(pattern);
    }

    private void validateURL(String uri, String parameterName) {
        try {
            new URL(uri);
            String splitRegex = File.separatorChar == '\\' ? "\\\\" : File.separator;
            fileSourceConfiguration.setProtocolForMoveAfterProcess(uri.split(splitRegex)[0]);
        } catch (MalformedURLException e) {
            throw new SiddhiAppCreationException(String.format("Provided uri for '%s' parameter '%s' is invalid.",
                    parameterName, uri), e);
        }
    }
}
