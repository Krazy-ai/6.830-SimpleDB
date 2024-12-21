package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.DbException;
import simpledb.common.Debug;
import simpledb.common.Permissions;
import simpledb.execution.OpIterator;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.*;


/**
 * HeapFile is an implementation of a DbFile that stores a collection of tuples
 * in no particular order. Tuples are stored on pages, each of which is a fixed
 * size, and the file is simply a collection of those pages. HeapFile works
 * closely with HeapPage. The format of HeapPages is described in the HeapPage
 * constructor.
 * 
 * @see HeapPage#HeapPage
 * @author Sam Madden
 */
public class HeapFile implements DbFile {

    private File file;
    private TupleDesc td;
    private int numPages;

    /**
     * Constructs a heap file backed by the specified file.
     * 
     * @param f
     *            the file that stores the on-disk backing store for this heap
     *            file.
     */
    public HeapFile(File f, TupleDesc td) {
        // some code goes here
        this.file = f;
        this.td = td;
        this.numPages = numPages();
    }

    /**
     * Returns the File backing this HeapFile on disk.
     * 
     * @return the File backing this HeapFile on disk.
     */
    public File getFile() {
        // some code goes here
        return this.file;
    }

    /**
     * Returns an ID uniquely identifying this HeapFile. Implementation note:
     * you will need to generate this tableid somewhere to ensure that each
     * HeapFile has a "unique id," and that you always return the same value for
     * a particular HeapFile. We suggest hashing the absolute file name of the
     * file underlying the heapfile, i.e. f.getAbsoluteFile().hashCode().
     * 
     * @return an ID uniquely identifying this HeapFile.
     */
    public int getId() {
        // some code goes here
        return this.file.getAbsolutePath().hashCode();
    }

    /**
     * Returns the TupleDesc of the table stored in this DbFile.
     * 
     * @return TupleDesc of this DbFile.
     */
    public TupleDesc getTupleDesc() {
        // some code goes here
        return this.td;
    }

    // see DbFile.java for javadocs
    public Page readPage(PageId pid) {
        // some code goes here
        long offset = (long) pid.getPageNumber() * BufferPool.getPageSize();
        Page page;
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
            byte[] data = new byte[BufferPool.getPageSize()];
            randomAccessFile.seek(offset);
            randomAccessFile.read(data);
            page = new HeapPage((HeapPageId) pid, data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return page;
    }

    // see DbFile.java for javadocs
    public void writePage(Page page) throws IOException {
        // some code goes here
        // not necessary for lab1
        HeapPageId pageId =(HeapPageId) page.getId();
        long offset = (long) pageId.getPageNumber() * BufferPool.getPageSize();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.seek(offset);
            randomAccessFile.write(ByteBuffer.wrap(page.getPageData()).array());
        }catch (IOException e) {
            throw new IOException(e);
        }
    }

    /**
     * Returns the number of pages in this HeapFile.
     */
    public int numPages() {
        // some code goes here
        long length = this.file.length();
        return ((int) Math.ceil(length * 1.0 / BufferPool.getPageSize()));
    }

    private void appendEmptyPage() throws IOException {
        HeapPageId newPageId = new HeapPageId(getId(), numPages);
        long offset = (long) newPageId.getPageNumber() * BufferPool.getPageSize();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw")) {
            randomAccessFile.seek(offset);
            randomAccessFile.write(ByteBuffer.wrap(HeapPage.createEmptyPageData()).array());
        }catch (IOException e) {
            throw new IOException(e);
        }
        numPages++;
    }
    // see DbFile.java for javadocs
    public List<Page> insertTuple(TransactionId tid, Tuple t)
            throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        BufferPool bufferPool = Database.getBufferPool();
        int tableId = getId();
        ArrayList<Page> res = new ArrayList<>(1);
        int idx = 0;
        while (true) {
            if(idx == numPages)
                appendEmptyPage();
            HeapPageId pageId = new HeapPageId(tableId, idx);
            boolean prevHasLock = bufferPool.holdsLock(tid, pageId);
            HeapPage page = (HeapPage) bufferPool.getPage(tid, pageId, Permissions.READ_ONLY);
            // 如果有空槽请求写，没有则释放锁。
            if(page.getNumEmptySlots() > 0) {
                page = (HeapPage) bufferPool.getPage(tid, pageId, Permissions.READ_WRITE); // 再以写访问
                page.insertTuple(t);
                page.markDirty(true, tid);
                res.add(page);
                return res;
            } else {
                // 之前没有锁，才可以提前释放。假设一个事务在一个线程内处理。
                if(!prevHasLock) {
                    bufferPool.unsafeReleasePage(tid, pageId);
                }
                //TODO 若一个事务在多个线程处理,则不能释放锁(因为不知道是否有其他线程获取了锁而访问页面). 测试用例并无出现这种情况.
                idx++; // 访问下一页
            }
        }
    }

    // see DbFile.java for javadocs
    public ArrayList<Page> deleteTuple(TransactionId tid, Tuple t) throws DbException,
            TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        BufferPool bufferPool = Database.getBufferPool();
        ArrayList<Page> res = new ArrayList<>(1);
        HeapPageId pageId = (HeapPageId)t.getRecordId().getPageId();
        HeapPage page = (HeapPage) bufferPool.getPage(tid, pageId, Permissions.READ_WRITE);
        page.deleteTuple(t);
        page.markDirty(true, tid);
        res.add(page);
        return res;
    }

    // see DbFile.java for javadocs
    public DbFileIterator iterator(TransactionId tid) {
        // some code goes here
        return new HeapFileIterator(this, tid);
    }
}

class HeapFileIterator extends AbstractDbFileIterator {

    private final HeapFile file;
    private final TransactionId tid;
    private Iterator<Tuple> curIterator;
    private PageId curPageId;

    public HeapFileIterator(HeapFile file, TransactionId tid) {
        this.file = file;
        this.tid = tid;
    }
    @Override
    public void open() throws DbException, TransactionAbortedException {
        this.curPageId = new HeapPageId(file.getId(), 0);
        HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, curPageId, Permissions.READ_ONLY);
        curIterator = page.iterator();
    }

    @Override
    protected Tuple readNext() throws TransactionAbortedException, DbException {
        while(curIterator!=null){
            if(curIterator.hasNext()){
                return curIterator.next();
            }
            curIterator=null;
            if(curPageId.getPageNumber()+1<file.numPages()){
                curPageId = new HeapPageId(file.getId(), curPageId.getPageNumber()+1);
                HeapPage page = (HeapPage) Database.getBufferPool().getPage(tid, curPageId, Permissions.READ_ONLY);
                curIterator = page.iterator();
            }
        }
        return null;
    }

    @Override
    public void rewind() throws DbException, TransactionAbortedException {
        close();
        open();
    }

    @Override
    public void close() {
        super.close();
        curIterator = null;
        curPageId = null;
    }
}

