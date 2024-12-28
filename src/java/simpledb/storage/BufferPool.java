package simpledb.storage;

import simpledb.common.Database;
import simpledb.common.Permissions;
import simpledb.common.DbException;
import simpledb.common.DeadlockException;
import simpledb.transaction.PageLockManager;
import simpledb.transaction.TransactionAbortedException;
import simpledb.transaction.TransactionId;

import java.io.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BufferPool manages the reading and writing of pages into memory from
 * disk. Access methods call into it to retrieve pages, and it fetches
 * pages from the appropriate location.
 * <p>
 * The BufferPool is also responsible for locking;  when a transaction fetches
 * a page, BufferPool checks that the transaction has the appropriate
 * locks to read/write the page.
 * 
 * @Threadsafe, all fields are final
 */
public class BufferPool {
    /** Bytes per page, including header. */
    private static final int DEFAULT_PAGE_SIZE = 4096;

    private static int pageSize = DEFAULT_PAGE_SIZE;
    
    /** Default number of pages passed to the constructor. This is used by
    other classes. BufferPool should use the numPages argument to the
    constructor instead. */
    public static final int DEFAULT_PAGES = 50;

    private PageLockManager pageLockManager;
    private final Integer numPages;
    private final Map<PageId, LRUnode> pageCache;
    private final Map<Long, Integer> tRandomTimeout = new ConcurrentHashMap<>();//并发
    private static final int TIMEOUT_MILLISECONDS = 1500; // 认为死锁超时的时间
    private final Random randomTimeout = new Random();
    private LRUnode head;
    private LRUnode tail;

    /** TODO 对map操作加锁，也就意味着对LRU队列操作加锁。BufferPool类每个方法不要sync，会死锁。并发操作均使用mapMonitor!!!*/
    private final ReentrantLock mapMonitor = new ReentrantLock();
    class LRUnode{
        PageId pid;
        Page page;
        LRUnode next;
        LRUnode prev;

        public LRUnode() {
        }

        public LRUnode(PageId pid, Page page) {
            this.pid = pid;
            this.page = page;
        }
    }

    /**
     * Creates a BufferPool that caches up to numPages pages.
     *
     * @param numPages maximum number of pages in this buffer pool.
     */
    public BufferPool(int numPages) {
        // some code goes here
        this.numPages = numPages;
        this.pageCache = new ConcurrentHashMap<>();
        head = new LRUnode();
        tail = new LRUnode();
        head.next = tail;
        tail.prev = head;
        pageLockManager = new PageLockManager();
    }
    
    public static int getPageSize() {
      return pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void setPageSize(int pageSize) {
    	BufferPool.pageSize = pageSize;
    }
    
    // THIS FUNCTION SHOULD ONLY BE USED FOR TESTING!!
    public static void resetPageSize() {
    	BufferPool.pageSize = DEFAULT_PAGE_SIZE;
    }

    /**
     * Retrieve the specified page with the associated permissions.
     * Will acquire a lock and may block if that lock is held by another
     * transaction.
     * <p>
     * The retrieved page should be looked up in the buffer pool.  If it
     * is present, it should be returned.  If it is not present, it should
     * be added to the buffer pool and returned.  If there is insufficient
     * space in the buffer pool, a page should be evicted and the new page
     * should be added in its place.
     *
     * @param tid the ID of the transaction requesting the page
     * @param pid the ID of the requested page
     * @param perm the requested permissions on the page
     */
    public Page getPage(TransactionId tid, PageId pid, Permissions perm)
        throws TransactionAbortedException, DbException {
        // some code goes here
        int acquireType = perm == Permissions.READ_ONLY ? PageLockManager.PageLock.SHARE : PageLockManager.PageLock.EXCLUSIVE;
        long start = System.currentTimeMillis();
        while (true) {
            try {
                if (pageLockManager.acquireLock(pid, tid, acquireType)) {
                    mapMonitor.lock();
                    try {
                        LRUnode node = pageCache.getOrDefault(pid, null);
                        if(node == null){
                            DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
                            Page page = dbFile.readPage(pid);
                            /*if(perm==Permissions.READ_WRITE){
                                page.markDirty(true, tid);
                            }*/
                            LRUnode newNode = new LRUnode(pid, page);
                            pageCache.put(pid, newNode);
                            addHead(newNode);
                            if (pageCache.size() > numPages) {
                                evictPage();
                            }
                            return newNode.page;
                        }else{
                            /*TODO 加上的话 BTreeFileInsertTest会报全脏页的错误
                               （perm只用于区分读锁写锁，加入脏页的时机是在真正发生修改时？）
                            if(perm==Permissions.READ_WRITE){
                                node.page.markDirty(true, tid);
                            }*/
                            moveToHead(node);
                            return node.page;
                        }
                    } finally {
                        mapMonitor.unlock();
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            // 如果未能获取到锁，判断是否超时
            long now = System.currentTimeMillis();
            if (now - start > getTransactionTimeout(tid.getId())) {
                throw new TransactionAbortedException();
            }
            //添加短暂的休眠，避免 CPU 过度占用
            // 随机化减少竞争
            try {
                Thread.sleep(20 + ThreadLocalRandom.current().nextInt(60));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();// 中断处理
            }
        }
    }

    // 随机化超时时间
    private int getTransactionTimeout(long tid) {
        tRandomTimeout.putIfAbsent(tid, randomTimeout.nextInt(1000) + TIMEOUT_MILLISECONDS);
        Integer res = tRandomTimeout.get(tid); // 以防并发问题
        return res == null ? TIMEOUT_MILLISECONDS : res;
    }
    /**
     * Releases the lock on a page.
     * Calling this is very risky, and may result in wrong behavior. Think hard
     * about who needs to call this and why, and why they can run the risk of
     * calling it.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param pid the ID of the page to unlock
     */
    public  void unsafeReleasePage(TransactionId tid, PageId pid) {
        // some code goes here
        // not necessary for lab1|lab2
        pageLockManager.releaseLock(pid,tid);
    }

    /**
     * Release all locks associated with a given transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     */
    public void transactionComplete(TransactionId tid) {
        // some code goes here
        // not necessary for lab1|lab2
        transactionComplete(tid, true);
    }

    /** Return true if the specified transaction has a lock on the specified page */
    public boolean holdsLock(TransactionId tid, PageId p) {
        // some code goes here
        // not necessary for lab1|lab2
        return pageLockManager.isHoldLock(p, tid);
    }

    /**
     * Commit or abort a given transaction; release all locks associated to
     * the transaction.
     *
     * @param tid the ID of the transaction requesting the unlock
     * @param commit a flag indicating whether we should commit or abort
     */
    public void transactionComplete(TransactionId tid, boolean commit) {
        // some code goes here
        // not necessary for lab1|lab2
        if (commit) {
            try {
                flushPages(tid);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // 回滚事务,从磁盘加载旧页完成回滚
            restorePages(tid);
        }
        pageLockManager.completeTransaction(tid);
        tRandomTimeout.remove(tid.getId());
    }

    private synchronized void restorePages(TransactionId tid) {
        for (Map.Entry<PageId, LRUnode> entry : pageCache.entrySet()) {
            PageId pid = entry.getKey();
            Page page = entry.getValue().page;
            if (page.isDirty() == tid) {
                discardPage(pid);
                try {
                    getPage(tid, pid, Permissions.READ_ONLY);
                } catch (TransactionAbortedException e) {
                    throw new RuntimeException(e);
                } catch (DbException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Add a tuple to the specified table on behalf of transaction tid.  Will
     * acquire a write lock on the page the tuple is added to and any other 
     * pages that are updated (Lock acquisition is not needed for lab2). 
     * May block if the lock(s) cannot be acquired.
     * 
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction adding the tuple
     * @param tableId the table to add the tuple to
     * @param t the tuple to add
     */
    public void insertTuple(TransactionId tid, int tableId, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
        dbFile.insertTuple(tid, t);
    }

    /**
     * Remove the specified tuple from the buffer pool.
     * Will acquire a write lock on the page the tuple is removed from and any
     * other pages that are updated. May block if the lock(s) cannot be acquired.
     *
     * Marks any pages that were dirtied by the operation as dirty by calling
     * their markDirty bit, and adds versions of any pages that have 
     * been dirtied to the cache (replacing any existing versions of those pages) so 
     * that future requests see up-to-date pages. 
     *
     * @param tid the transaction deleting the tuple.
     * @param t the tuple to delete
     */
    public  void deleteTuple(TransactionId tid, Tuple t)
        throws DbException, IOException, TransactionAbortedException {
        // some code goes here
        // not necessary for lab1
        int tableId = t.getRecordId().getPageId().getTableId();
        DbFile dbFile = Database.getCatalog().getDatabaseFile(tableId);
        dbFile.deleteTuple(tid, t);
    }

    /**
     * Flush all dirty pages to disk.
     * NB: Be careful using this routine -- it writes dirty data to disk so will
     *     break simpledb if running in NO STEAL mode.
     */
    public synchronized void flushAllPages() throws IOException {
        // some code goes here
        // not necessary for lab1
        for (PageId pageId : pageCache.keySet()) {
            flushPage(pageId);
        }
    }

    /** Remove the specific page id from the buffer pool.
        Needed by the recovery manager to ensure that the
        buffer pool doesn't keep a rolled back page in its
        cache.
        
        Also used by B+ tree files to ensure that deleted pages
        are removed from the cache so they can be reused safely
    */
    public synchronized void discardPage(PageId pid) {
        // some code goes here
        // not necessary for lab1
        LRUnode node = pageCache.get(pid);
        if(node != null) {
            removeNode(node);
            pageCache.remove(pid);
        }
    }

    /**
     * Flushes a certain page to disk
     * @param pid an ID indicating the page to flush
     */
    private synchronized  void flushPage(PageId pid) throws IOException {
        // some code goes here
        // not necessary for lab1
        LRUnode node = pageCache.get(pid);
        if(node == null) return;
        TransactionId dirtyTId = node.page.isDirty();
        if(dirtyTId != null) {
            DbFile dbFile = Database.getCatalog().getDatabaseFile(pid.getTableId());
            Database.getLogFile().logWrite(dirtyTId, node.page.getBeforeImage(), node.page);
            Database.getLogFile().force();
            dbFile.writePage(node.page);
            node.page.markDirty(false, null);
        }

    }

    /** Write all pages of the specified transaction to disk.
     */
    public synchronized  void flushPages(TransactionId tid) throws IOException {
        // some code goes here
        // not necessary for lab1|lab2
        for (Map.Entry<PageId, LRUnode> entry : pageCache.entrySet()) {
            Page page = entry.getValue().page;
            page.setBeforeImage();
            if (page.isDirty() == tid) {
                flushPage(page.getId());
            }
        }
    }

    /**
     * Discards a page from the buffer pool.
     * Flushes the page to disk to ensure dirty pages are updated on disk.
     */
    private synchronized  void evictPage() throws DbException, IOException {
        // some code goes here
        // not necessary for lab1
        mapMonitor.lock();
        try {
            LRUnode rm = tail.prev;
            while (rm != head.next && rm.page.isDirty() != null) {
                rm = rm.prev;
            }
            //TODO 驱逐之前缓存池中是有pagesize+1个页面的，新加入的页面是head.next，所以遍历到head.next即可（不是head)
            //对于testAllDirtyFails测试用例，读的时候会从表的起始开始读，一定会有一个刚读的不脏的页面在队头  ？？？
            if(rm == head.next){
                throw new DbException("all pages are dirty");
            }
            removeNode(rm);
            flushPage(rm.pid);
            pageCache.remove(rm.pid);
            rm.page.markDirty(false, null);
        } catch (DbException e) {
            throw new RuntimeException(e);
        } finally {
            mapMonitor.unlock();
        }
    }

    void addHead(LRUnode node) {
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
        node.prev = head;
    }

    void removeNode(LRUnode node) {
        node.next.prev = node.prev;
        node.prev.next = node.next;
    }

    void moveToHead(LRUnode node) {
        removeNode(node);
        addHead(node);
    }

    LRUnode removeTail() {
        LRUnode res = tail.prev;
        removeNode(res);
        return res;
    }
}
