/*
 * Copyright 2009-2012 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.storage.am.lsm.rtree.impls;

import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.io.IIOManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.AbstractLSMIndexFileManager;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.TreeIndexFactory;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;

public class LSMRTreeWithAntiMatterTuplesFileManager extends AbstractLSMIndexFileManager {

    public LSMRTreeWithAntiMatterTuplesFileManager(IIOManager ioManager, IFileMapProvider fileMapProvider,
            FileReference file, TreeIndexFactory<? extends ITreeIndex> rtreeFactory, int startIODeviceIndex) {
        super(ioManager, fileMapProvider, file, rtreeFactory, startIODeviceIndex);
    }
}
