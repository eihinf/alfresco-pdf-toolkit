/*
 * Copyright 2008-2012 Alfresco Software Limited.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * This file is part of an unsupported extension to Alfresco.
 */

package org.alfresco.extension.pdftoolkit.repo.action.executer;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.action.ParameterDefinitionImpl;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.model.FileExistsException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.TempFileProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFMergerUtility;


/**
 * Append PDF action executer
 * 
 * @author Jared Ottley
 * 
 */

public class PDFAppendActionExecuter
    extends BasePDFActionExecuter

{

    /**
     * The logger
     */
    private static Log         logger                   = LogFactory.getLog(PDFAppendActionExecuter.class);

    /**
     * Action constants
     */
    public static final String NAME                     = "pdf-append";
    public static final String PARAM_TARGET_NODE        = "target-node";
    public static final String PARAM_DESTINATION_FOLDER = "destination-folder";
    public static final String PARAM_DESTINATION_NAME   = "destination-name";


    /**
     * Add parameter definitions
     */
    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        paramList.add(new ParameterDefinitionImpl(PARAM_TARGET_NODE, DataTypeDefinition.NODE_REF, true, getParamDisplayLabel(PARAM_TARGET_NODE)));
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_FOLDER, DataTypeDefinition.NODE_REF, true, getParamDisplayLabel(PARAM_DESTINATION_FOLDER)));
        paramList.add(new ParameterDefinitionImpl(PARAM_DESTINATION_NAME, DataTypeDefinition.TEXT, true, getParamDisplayLabel(PARAM_DESTINATION_NAME)));
    }


    /**
     * @see org.alfresco.repo.action.executer.ActionExecuterAbstractBase#executeImpl(org.alfresco.service.cmr.repository.NodeRef,
     * org.alfresco.service.cmr.repository.NodeRef)
     */
    @Override
    protected void executeImpl(Action ruleAction, NodeRef actionedUponNodeRef)
    {
        if (serviceRegistry.getNodeService().exists(actionedUponNodeRef) == false)
        {
            // node doesn't exist - can't do anything
            return;
        }

        NodeRef targetNodeRef = (NodeRef)ruleAction.getParameterValue(PARAM_TARGET_NODE);

        if (serviceRegistry.getNodeService().exists(targetNodeRef) == false)
        {
            // target node doesn't exist - can't do anything
            return;
        }

        ContentReader contentReader = getReader(actionedUponNodeRef);
        ContentReader targetContentReader = getReader(targetNodeRef);

        if (contentReader != null && targetContentReader != null)
        {
            // Do the work....split the PDF
            doAppend(ruleAction, actionedUponNodeRef, targetNodeRef, contentReader, targetContentReader);

            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Can't execute rule: \n" + "   node: " + actionedUponNodeRef + "\n" + "   reader: "
                                 + contentReader + "\n" + "   action: " + this);
                }
            }
        }
    }


    /**
     * @see org.alfresco.repo.action.executer.TransformActionExecuter#doTransform(org.alfresco.service.cmr.action.Action,
     * org.alfresco.service.cmr.repository.ContentReader,
     * org.alfresco.service.cmr.repository.ContentWriter)
     */
    protected void doAppend(Action ruleAction, NodeRef actionedUponNodeRef, NodeRef targetNodeRef, ContentReader contentReader,
            ContentReader targetContentReader)
    {

        Map<String, Object> options = new HashMap<String, Object>(5);
        options.put(PARAM_TARGET_NODE, ruleAction.getParameterValue(PARAM_TARGET_NODE));
        options.put(PARAM_DESTINATION_FOLDER, ruleAction.getParameterValue(PARAM_DESTINATION_FOLDER));
        options.put(PARAM_DESTINATION_NAME, ruleAction.getParameterValue(PARAM_DESTINATION_NAME));

        try
        {
            this.action(ruleAction, actionedUponNodeRef, targetNodeRef, contentReader, targetContentReader, options);
        }
        catch (AlfrescoRuntimeException e)
        {
            e.printStackTrace();
        }
    }


    /**
     * @param reader
     * @param writer
     * @param options
     * @throws Exception
     */
    protected final void action(Action ruleAction, NodeRef actionedUponNodeRef, NodeRef targetNodeRef, ContentReader reader,
            ContentReader targetContentReader, Map<String, Object> options)
        throws AlfrescoRuntimeException
    {
        PDDocument pdf = null;
        PDDocument pdfTarget = null;
        InputStream is = null;
        InputStream tis = null;
        File tempDir = null;
        ContentWriter writer = null;

        try
        {
            is = reader.getContentInputStream();
            tis = targetContentReader.getContentInputStream();
            // stream the document in
            pdf = PDDocument.load(is);
            pdfTarget = PDDocument.load(tis);
            // Append the PDFs
            PDFMergerUtility merger = new PDFMergerUtility();
            merger.appendDocument(pdfTarget, pdf);
            merger.setDestinationFileName(options.get(PARAM_DESTINATION_NAME).toString());
            merger.mergeDocuments();

            // build a temp dir name based on the ID of the noderef we are
            // importing
            File alfTempDir = TempFileProvider.getTempDir();
            tempDir = new File(alfTempDir.getPath() + File.separatorChar + actionedUponNodeRef.getId());
            tempDir.mkdir();

            String fileName = options.get(PARAM_DESTINATION_NAME).toString();
            pdfTarget.save(tempDir + "" + File.separatorChar + fileName + FILE_EXTENSION);

            for (File file : tempDir.listFiles())
            {
                try
                {
                    if (file.isFile())
                    {
                        // What is the file name?
                        String filename = file.getName();

                        // Get a writer and prep it for putting it back into the
                        // repo
                        writer = getWriter(filename, (NodeRef)ruleAction.getParameterValue(PARAM_DESTINATION_FOLDER));
                        writer.setEncoding(reader.getEncoding()); // original
                                                                  // encoding
                        writer.setMimetype(FILE_MIMETYPE);

                        // Put it in the repo
                        writer.putContent(file);

                        // Clean up
                        file.delete();
                    }
                }
                catch (FileExistsException e)
                {
                    throw new AlfrescoRuntimeException("Failed to process file.", e);
                }
            }
        }
        // TODO add better handling
        catch (COSVisitorException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        finally
        {
            if (pdf != null)
            {
                try
                {
                    pdf.close();
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }
            if (pdfTarget != null)
            {
                try
                {
                    pdfTarget.close();
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }
            if (is != null)
            {
                try
                {
                    is.close();
                }
                catch (Throwable e)
                {
                    e.printStackTrace();
                }
            }

            if (tempDir != null)
            {
                tempDir.delete();
            }
        }

        // TODO add debug
        if (logger.isDebugEnabled())
        {

        }
    }
}
