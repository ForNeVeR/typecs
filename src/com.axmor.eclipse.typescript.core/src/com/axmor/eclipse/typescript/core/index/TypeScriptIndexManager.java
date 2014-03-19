/*******************************************************************************
 * Copyright (c) 2014 Axmor Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package com.axmor.eclipse.typescript.core.index;

import org.apache.lucene.store.NRTCachingDirectory;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

import com.axmor.eclipse.typescript.core.Activator;
import com.axmor.eclipse.typescript.core.TypeScriptAPIFactory;
import com.axmor.eclipse.typescript.core.TypeScriptResources;

/**
 * @author Konstantin Zaitcev
 */
public class TypeScriptIndexManager implements IResourceChangeListener {
    /** Index job. */
    private IndexJob job;

    /** Caching indexed directory. */
    private NRTCachingDirectory idxDir;
    
    /**
     * @return the idxDir
     */
    public NRTCachingDirectory getIdxDir() {
        return idxDir;
    }

    /**
     * Starts indexing process.
     */
    public void startIndex() {
        job = new IndexJob();
        idxDir = job.getIndexDirectory();
        job.setSystem(true);
        job.schedule();

        ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
    }

    /**
     * Stops indexing process.
     */
    public void stopIndex() {
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(this);
        if (job.cancel()) {
            job.getIndexer().close();
        }
    }
    
    /**
     * Flushes changes on disk.
     */
    public void flush() {
        job.getIndexer().flush();
    }

    @Override
    public void resourceChanged(IResourceChangeEvent event) {
        try {
            event.getDelta().accept(new IResourceDeltaVisitor() {
                @Override
                public boolean visit(IResourceDelta delta) throws CoreException {
                    IResource resource = delta.getResource();
                    if (resource != null && resource.getType() == IResource.FILE) {
                        if (TypeScriptResources.isTypeScriptFile(resource.getName())) {
                            if (delta.getKind() == IResourceDelta.REMOVED) {
                                job.getIndexer().removeFromIndex(resource.getFullPath().toString());
                            } else {
                                job.getChangedResources().add(resource.getFullPath().toString());
                            }
                            if (delta.getKind() == IResourceDelta.ADDED) {
                                TypeScriptAPIFactory.getTypeScriptAPI(resource.getProject()).addFile((IFile) resource);
                            }
                        }
                    }
                    return true;
                }
            });
        } catch (CoreException e) {
            Activator.error(e);
        }
    }
}