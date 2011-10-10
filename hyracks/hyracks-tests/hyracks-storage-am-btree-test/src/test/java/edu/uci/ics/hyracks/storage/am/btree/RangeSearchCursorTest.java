/*
 * Copyright 2009-2010 by The Regents of the University of California
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

package edu.uci.ics.hyracks.storage.am.btree;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.TreeSet;

import org.junit.Before;
import org.junit.Test;

import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.context.IHyracksStageletContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.dataflow.value.TypeTrait;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.comparators.IntegerBinaryComparatorFactory;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeInteriorFrame;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeFieldPrefixNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTreeException;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTreeOpContext;
import edu.uci.ics.hyracks.storage.am.btree.impls.RangePredicate;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTreeRangeSearchCursor;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrame;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.frames.LIFOMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.freepage.LinkedListFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.am.common.tuples.TypeAwareTupleWriterFactory;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.file.IFileMapProvider;
import edu.uci.ics.hyracks.test.support.TestStorageManagerComponentHolder;
import edu.uci.ics.hyracks.test.support.TestUtils;

public class RangeSearchCursorTest extends AbstractBTreeTest {
    private static final int PAGE_SIZE = 256;
    private static final int NUM_PAGES = 10;
    private static final int MAX_OPEN_FILES = 10;
    private static final int HYRACKS_FRAME_SIZE = 128;

    // declare fields
    int fieldCount = 2;
    ITypeTrait[] typeTraits = new ITypeTrait[fieldCount];

    TypeAwareTupleWriterFactory tupleWriterFactory = new TypeAwareTupleWriterFactory(typeTraits);
    ITreeIndexFrameFactory leafFrameFactory = new BTreeNSMLeafFrameFactory(tupleWriterFactory);
    ITreeIndexFrameFactory interiorFrameFactory = new BTreeNSMInteriorFrameFactory(tupleWriterFactory);
    ITreeIndexMetaDataFrameFactory metaFrameFactory = new LIFOMetaDataFrameFactory();

    IBTreeLeafFrame leafFrame = (IBTreeLeafFrame) leafFrameFactory.createFrame();
    IBTreeInteriorFrame interiorFrame = (IBTreeInteriorFrame) interiorFrameFactory.createFrame();
    ITreeIndexMetaDataFrame metaFrame = metaFrameFactory.createFrame();

    IHyracksStageletContext ctx = TestUtils.create(HYRACKS_FRAME_SIZE);
    ByteBuffer frame = ctx.allocateFrame();
    FrameTupleAppender appender = new FrameTupleAppender(ctx.getFrameSize());

    ISerializerDeserializer[] recDescSers = { IntegerSerializerDeserializer.INSTANCE,
            IntegerSerializerDeserializer.INSTANCE };
    RecordDescriptor recDesc = new RecordDescriptor(recDescSers);
    IFrameTupleAccessor accessor = new FrameTupleAccessor(ctx.getFrameSize(), recDesc);
    FrameTupleReference tuple = new FrameTupleReference();

    Random rnd = new Random(50);

    @Before
    public void setUp() {
        typeTraits[0] = new TypeTrait(4);
        typeTraits[1] = new TypeTrait(4);
        accessor.reset(frame);
    }

    @Test
    public void uniqueIndexTest() throws Exception {

        LOGGER.info("TESTING RANGE SEARCH CURSOR ON UNIQUE INDEX");

        TestStorageManagerComponentHolder.init(PAGE_SIZE, NUM_PAGES, MAX_OPEN_FILES);
        IBufferCache bufferCache = TestStorageManagerComponentHolder.getBufferCache(ctx);
        IFileMapProvider fmp = TestStorageManagerComponentHolder.getFileMapProvider(ctx);
        FileReference file = new FileReference(new File(fileName));
        bufferCache.createFile(file);
        int fileId = fmp.lookupFileId(file);
        bufferCache.openFile(fileId);

        // declare keys
        int keyFieldCount = 1;
        IBinaryComparator[] cmps = new IBinaryComparator[keyFieldCount];
        cmps[0] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();

        MultiComparator cmp = new MultiComparator(typeTraits, cmps);

        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, fileId, 0, metaFrameFactory);

        BTree btree = new BTree(bufferCache, freePageManager, interiorFrameFactory, leafFrameFactory, cmp);
        btree.create(fileId, leafFrame, metaFrame);
        btree.open(fileId);

        ArrayTupleBuilder tb = new ArrayTupleBuilder(cmp.getFieldCount());
        DataOutput dos = tb.getDataOutput();

        BTreeOpContext insertOpCtx = btree.createOpContext(IndexOp.INSERT, leafFrame, interiorFrame, metaFrame);

        // generate keys
        int numKeys = 50;
        int maxKey = 1000;
        TreeSet<Integer> uniqueKeys = new TreeSet<Integer>();
        ArrayList<Integer> keys = new ArrayList<Integer>();
        while (uniqueKeys.size() < numKeys) {
            int key = rnd.nextInt() % maxKey;
            uniqueKeys.add(key);
        }
        for (Integer i : uniqueKeys) {
            keys.add(i);
        }

        // insert keys into btree
        for (int i = 0; i < keys.size(); i++) {

            tb.reset();
            IntegerSerializerDeserializer.INSTANCE.serialize(keys.get(i).intValue(), dos);
            tb.addFieldEndOffset();
            IntegerSerializerDeserializer.INSTANCE.serialize(i, dos);
            tb.addFieldEndOffset();

            appender.reset(frame, true);
            appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());

            tuple.reset(accessor, 0);

            try {
                btree.insert(tuple, insertOpCtx);
            } catch (BTreeException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // btree.printTree(leafFrame, interiorFrame, recDescSers);

        int minSearchKey = -100;
        int maxSearchKey = 100;

        // forward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);

        // backward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);

        btree.close();
        bufferCache.closeFile(fileId);
        bufferCache.close();
    }

    @Test
    public void nonUniqueIndexTest() throws Exception {

        LOGGER.info("TESTING RANGE SEARCH CURSOR ON NONUNIQUE INDEX");

        TestStorageManagerComponentHolder.init(PAGE_SIZE, NUM_PAGES, MAX_OPEN_FILES);
        IBufferCache bufferCache = TestStorageManagerComponentHolder.getBufferCache(ctx);
        IFileMapProvider fmp = TestStorageManagerComponentHolder.getFileMapProvider(ctx);
        FileReference file = new FileReference(new File(fileName));
        bufferCache.createFile(file);
        int fileId = fmp.lookupFileId(file);
        bufferCache.openFile(fileId);

        // declare keys
        int keyFieldCount = 2;
        IBinaryComparator[] cmps = new IBinaryComparator[keyFieldCount];
        cmps[0] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();
        cmps[1] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();

        MultiComparator cmp = new MultiComparator(typeTraits, cmps);

        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, fileId, 0, metaFrameFactory);

        BTree btree = new BTree(bufferCache, freePageManager, interiorFrameFactory, leafFrameFactory, cmp);
        btree.create(fileId, leafFrame, metaFrame);
        btree.open(fileId);

        ArrayTupleBuilder tb = new ArrayTupleBuilder(cmp.getFieldCount());
        DataOutput dos = tb.getDataOutput();

        BTreeOpContext insertOpCtx = btree.createOpContext(IndexOp.INSERT, leafFrame, interiorFrame, metaFrame);

        // generate keys
        int numKeys = 50;
        int maxKey = 10;
        ArrayList<Integer> keys = new ArrayList<Integer>();
        for (int i = 0; i < numKeys; i++) {
            int k = rnd.nextInt() % maxKey;
            keys.add(k);
        }
        Collections.sort(keys);

        // insert keys into btree
        for (int i = 0; i < keys.size(); i++) {

            tb.reset();
            IntegerSerializerDeserializer.INSTANCE.serialize(keys.get(i).intValue(), dos);
            tb.addFieldEndOffset();
            IntegerSerializerDeserializer.INSTANCE.serialize(i, dos);
            tb.addFieldEndOffset();

            appender.reset(frame, true);
            appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());

            tuple.reset(accessor, 0);

            try {
                btree.insert(tuple, insertOpCtx);
            } catch (BTreeException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // btree.printTree(leafFrame, interiorFrame, recDescSers);

        int minSearchKey = -100;
        int maxSearchKey = 100;

        // forward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);

        // backward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);

        btree.close();
        bufferCache.closeFile(fileId);
        bufferCache.close();
    }

    @Test
    public void nonUniqueFieldPrefixIndexTest() throws Exception {

        LOGGER.info("TESTING RANGE SEARCH CURSOR ON NONUNIQUE FIELD-PREFIX COMPRESSED INDEX");

        ITreeIndexFrameFactory leafFrameFactory = new BTreeFieldPrefixNSMLeafFrameFactory(tupleWriterFactory);
        IBTreeLeafFrame leafFrame = (IBTreeLeafFrame) leafFrameFactory.createFrame();

        TestStorageManagerComponentHolder.init(PAGE_SIZE, NUM_PAGES, MAX_OPEN_FILES);
        IBufferCache bufferCache = TestStorageManagerComponentHolder.getBufferCache(ctx);
        IFileMapProvider fmp = TestStorageManagerComponentHolder.getFileMapProvider(ctx);
        FileReference file = new FileReference(new File(fileName));
        bufferCache.createFile(file);
        int fileId = fmp.lookupFileId(file);
        bufferCache.openFile(fileId);

        // declare keys
        int keyFieldCount = 2;
        IBinaryComparator[] cmps = new IBinaryComparator[keyFieldCount];
        cmps[0] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();
        cmps[1] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();

        MultiComparator cmp = new MultiComparator(typeTraits, cmps);

        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, fileId, 0, metaFrameFactory);

        BTree btree = new BTree(bufferCache, freePageManager, interiorFrameFactory, leafFrameFactory, cmp);
        btree.create(fileId, leafFrame, metaFrame);
        btree.open(fileId);

        ArrayTupleBuilder tb = new ArrayTupleBuilder(cmp.getFieldCount());
        DataOutput dos = tb.getDataOutput();

        BTreeOpContext insertOpCtx = btree.createOpContext(IndexOp.INSERT, leafFrame, interiorFrame, metaFrame);

        // generate keys
        int numKeys = 50;
        int maxKey = 10;
        ArrayList<Integer> keys = new ArrayList<Integer>();
        for (int i = 0; i < numKeys; i++) {
            int k = rnd.nextInt() % maxKey;
            keys.add(k);
        }
        Collections.sort(keys);

        // insert keys into btree
        for (int i = 0; i < keys.size(); i++) {

            tb.reset();
            IntegerSerializerDeserializer.INSTANCE.serialize(keys.get(i).intValue(), dos);
            tb.addFieldEndOffset();
            IntegerSerializerDeserializer.INSTANCE.serialize(i, dos);
            tb.addFieldEndOffset();

            appender.reset(frame, true);
            appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize());

            tuple.reset(accessor, 0);

            try {
                btree.insert(tuple, insertOpCtx);
            } catch (BTreeException e) {
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // btree.printTree(leafFrame, interiorFrame, recDescSers);

        int minSearchKey = -100;
        int maxSearchKey = 100;

        // forward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, true, true, true, false);

        // backward searches
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, false, true, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, false, false);
        performSearches(keys, btree, leafFrame, interiorFrame, minSearchKey, maxSearchKey, false, true, true, false);

        btree.close();
        bufferCache.closeFile(fileId);
        bufferCache.close();
    }

    public RangePredicate createRangePredicate(int lk, int hk, boolean isForward, boolean lowKeyInclusive,
            boolean highKeyInclusive, MultiComparator cmp, ITypeTrait[] typeTraits) throws HyracksDataException {
        // build low and high keys
        ArrayTupleBuilder ktb = new ArrayTupleBuilder(1);
        DataOutput kdos = ktb.getDataOutput();

        ISerializerDeserializer[] keyDescSers = { IntegerSerializerDeserializer.INSTANCE };
        RecordDescriptor keyDesc = new RecordDescriptor(keyDescSers);
        IFrameTupleAccessor keyAccessor = new FrameTupleAccessor(ctx.getFrameSize(), keyDesc);
        keyAccessor.reset(frame);

        appender.reset(frame, true);

        // build and append low key
        ktb.reset();
        IntegerSerializerDeserializer.INSTANCE.serialize(lk, kdos);
        ktb.addFieldEndOffset();
        appender.append(ktb.getFieldEndOffsets(), ktb.getByteArray(), 0, ktb.getSize());

        // build and append high key
        ktb.reset();
        IntegerSerializerDeserializer.INSTANCE.serialize(hk, kdos);
        ktb.addFieldEndOffset();
        appender.append(ktb.getFieldEndOffsets(), ktb.getByteArray(), 0, ktb.getSize());

        // create tuplereferences for search keys
        FrameTupleReference lowKey = new FrameTupleReference();
        lowKey.reset(keyAccessor, 0);

        FrameTupleReference highKey = new FrameTupleReference();
        highKey.reset(keyAccessor, 1);

        IBinaryComparator[] searchCmps = new IBinaryComparator[1];
        searchCmps[0] = IntegerBinaryComparatorFactory.INSTANCE.createBinaryComparator();
        MultiComparator searchCmp = new MultiComparator(typeTraits, searchCmps);

        RangePredicate rangePred = new RangePredicate(isForward, lowKey, highKey, lowKeyInclusive, highKeyInclusive,
                searchCmp, searchCmp);
        return rangePred;
    }

    public void getExpectedResults(ArrayList<Integer> expectedResults, ArrayList<Integer> keys, int lk, int hk,
            boolean isForward, boolean lowKeyInclusive, boolean highKeyInclusive) {

        // special cases
        if (lk == hk && (!lowKeyInclusive || !highKeyInclusive))
            return;
        if (lk > hk)
            return;

        if (isForward) {
            for (int i = 0; i < keys.size(); i++) {
                if ((lk == keys.get(i) && lowKeyInclusive) || (hk == keys.get(i) && highKeyInclusive)) {
                    expectedResults.add(keys.get(i));
                    continue;
                }

                if (lk < keys.get(i) && hk > keys.get(i)) {
                    expectedResults.add(keys.get(i));
                    continue;
                }
            }
        } else {
            for (int i = keys.size() - 1; i >= 0; i--) {
                if ((lk == keys.get(i) && lowKeyInclusive) || (hk == keys.get(i) && highKeyInclusive)) {
                    expectedResults.add(keys.get(i));
                    continue;
                }

                if (lk < keys.get(i) && hk > keys.get(i)) {
                    expectedResults.add(keys.get(i));
                    continue;
                }
            }
        }
    }

    public boolean performSearches(ArrayList<Integer> keys, BTree btree, IBTreeLeafFrame leafFrame,
            IBTreeInteriorFrame interiorFrame, int minKey, int maxKey, boolean isForward, boolean lowKeyInclusive,
            boolean highKeyInclusive, boolean printExpectedResults) throws Exception {

        ArrayList<Integer> results = new ArrayList<Integer>();
        ArrayList<Integer> expectedResults = new ArrayList<Integer>();

        for (int i = minKey; i < maxKey; i++) {
            for (int j = minKey; j < maxKey; j++) {

                results.clear();
                expectedResults.clear();

                int lowKey = i;
                int highKey = j;

                ITreeIndexCursor rangeCursor = new BTreeRangeSearchCursor(leafFrame);
                RangePredicate rangePred = createRangePredicate(lowKey, highKey, isForward, lowKeyInclusive,
                        highKeyInclusive, btree.getMultiComparator(), btree.getMultiComparator().getTypeTraits());
                BTreeOpContext searchOpCtx = btree.createOpContext(IndexOp.SEARCH, leafFrame, interiorFrame, null);
                btree.search(rangeCursor, rangePred, searchOpCtx);

                try {
                    while (rangeCursor.hasNext()) {
                        rangeCursor.next();
                        ITupleReference frameTuple = rangeCursor.getTuple();
                        ByteArrayInputStream inStream = new ByteArrayInputStream(frameTuple.getFieldData(0),
                                frameTuple.getFieldStart(0), frameTuple.getFieldLength(0));
                        DataInput dataIn = new DataInputStream(inStream);
                        Integer res = IntegerSerializerDeserializer.INSTANCE.deserialize(dataIn);
                        results.add(res);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    rangeCursor.close();
                }

                getExpectedResults(expectedResults, keys, lowKey, highKey, isForward, lowKeyInclusive, highKeyInclusive);

                if (printExpectedResults) {
                    if (expectedResults.size() > 0) {
                        char l, u;

                        if (lowKeyInclusive)
                            l = '[';
                        else
                            l = '(';

                        if (highKeyInclusive)
                            u = ']';
                        else
                            u = ')';

                        LOGGER.info("RANGE: " + l + " " + lowKey + " , " + highKey + " " + u);
                        StringBuilder strBuilder = new StringBuilder();
                        for (Integer r : expectedResults) {
                            strBuilder.append(r + " ");
                        }
                        LOGGER.info(strBuilder.toString());
                    }
                }

                if (results.size() == expectedResults.size()) {
                    for (int k = 0; k < results.size(); k++) {
                        if (!results.get(k).equals(expectedResults.get(k))) {
                            LOGGER.info("DIFFERENT RESULTS AT: i=" + i + " j=" + j + " k=" + k);
                            LOGGER.info(results.get(k) + " " + expectedResults.get(k));
                            return false;
                        }
                    }
                } else {
                    LOGGER.info("UNEQUAL NUMBER OF RESULTS AT: i=" + i + " j=" + j);
                    LOGGER.info("RESULTS: " + results.size());
                    LOGGER.info("EXPECTED RESULTS: " + expectedResults.size());
                    return false;
                }
            }
        }

        return true;
    }
}