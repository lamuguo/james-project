/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.mailbox.lucene.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import javax.mail.Flags;
import javax.mail.Flags.Flag;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.SearchQuery.AddressType;
import org.apache.james.mailbox.model.SearchQuery.DateResolution;
import org.apache.james.mailbox.model.SearchQuery.Sort.SortClause;
import org.apache.james.mailbox.model.SimpleMailboxACL;
import org.apache.james.mailbox.store.MessageBuilder;
import org.apache.james.mailbox.store.SimpleMailboxMembership;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.Test;

public class LuceneMailboxMessageSearchIndexTest {

	private LuceneMessageSearchIndex index;
    
    private SimpleMailbox mailbox = new SimpleMailbox(0);
    private SimpleMailbox mailbox2 = new SimpleMailbox(1);
    private SimpleMailbox mailbox3 = new SimpleMailbox(2);
    private MailboxSession session;

    private static final String FROM_ADDRESS = "Harry <harry@example.org>";

    private static final String SUBJECT_PART = "Mixed";

    private static final String CUSTARD = "CUSTARD";

    private static final String RHUBARD = "Rhubard";

    private static final String BODY = "This is a simple email\r\n "
            + "It has " + RHUBARD + ".\r\n" + "It has " + CUSTARD + ".\r\n"
            + "It needs naught else.\r\n";

    private MessageUid uid1;
    private MessageUid uid2;
    private MessageUid uid3;
    private MessageUid uid4;
    private MessageUid uid5;

    protected boolean useLenient() {
        return true;
    }
    
    @Before
    public void setUp() throws Exception {
        index = new LuceneMessageSearchIndex(null, new TestId.Factory(), new RAMDirectory(), true, useLenient());
        index.setEnableSuffixMatch(true);
        Map<String, String> headersSubject = new HashMap<String, String>();
        headersSubject.put("Subject", "test (fwd)");
        headersSubject.put("From", "test99 <test99@localhost>");
        headersSubject.put("To", "test2 <test2@localhost>, test3 <test3@localhost>");

        Map<String, String> headersTest = new HashMap<String, String>();
        headersTest.put("Test", "test");
        headersTest.put("From", "test1 <test1@localhost>");
        headersTest.put("To", "test3 <test3@localhost>, test4 <test4@localhost>");
        headersTest.put("Cc", "test21 <test21@localhost>, test6 <test6@foobar>");

        Map<String, String> headersTestSubject =  new HashMap<String, String>();
        headersTestSubject.put("Test", "test");
        headersTestSubject.put("Subject", "test2");
        headersTestSubject.put("Date", "Thu, 14 Feb 1990 12:00:00 +0000 (GMT)");
        headersTestSubject.put("From", "test12 <test12@localhost>");
        headersTestSubject.put("Cc", "test211 <test21@localhost>, test6 <test6@foobar>");
        
        uid1 = MessageUid.of(1);
        SimpleMailboxMembership m = new SimpleMailboxMembership(new DefaultMessageId(), mailbox.getMailboxId(), uid1, 0, new Date(), 200, new Flags(Flag.ANSWERED), "My Body".getBytes(), headersSubject);
        index.add(null, mailbox, m);

        uid2 = MessageUid.of(1);
        SimpleMailboxMembership m2 = new SimpleMailboxMembership(new DefaultMessageId(), mailbox2.getMailboxId(), uid2, 0, new Date(), 20, new Flags(Flag.ANSWERED), "My Body".getBytes(), headersSubject);
        index.add(null, mailbox2, m2);
        
        uid3 = MessageUid.of(2);
        Calendar cal = Calendar.getInstance();
        cal.set(1980, 2, 10);
        SimpleMailboxMembership m3 = new SimpleMailboxMembership(new DefaultMessageId(), mailbox.getMailboxId(), uid3, 0, cal.getTime(), 20, new Flags(Flag.DELETED), "My Otherbody".getBytes(), headersTest);
        index.add(null, mailbox, m3);
        
        uid4 = MessageUid.of(3);
        Calendar cal2 = Calendar.getInstance();
        cal2.set(8000, 2, 10);
        SimpleMailboxMembership m4 = new SimpleMailboxMembership(new DefaultMessageId(), mailbox.getMailboxId(), uid4, 0, cal2.getTime(), 20, new Flags(Flag.DELETED), "My Otherbody2".getBytes(), headersTestSubject);
        index.add(null, mailbox, m4);
        
        uid5 = MessageUid.of(10);
        MessageBuilder builder = new MessageBuilder();
        builder.header("From", "test <user-from@domain.org>");
        builder.header("To", FROM_ADDRESS);
        builder.header("Subject", "A " + SUBJECT_PART + " Multipart Mail");
        builder.header("Date", "Thu, 14 Feb 2008 12:00:00 +0000 (GMT)");
        builder.body = Charset.forName("us-ascii").encode(BODY).array();
        builder.uid = uid5;
        builder.mailboxId = mailbox3.getMailboxId();
        
        index.add(null, mailbox3, builder.build());

        session = new MockMailboxSession("username");
    }
    


    @Test
    public void bodySearchShouldMatchPhraseInBody() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains(CUSTARD));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void bodySearchShouldNotMatchAbsentPhraseInBody() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains(CUSTARD + CUSTARD));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).isEmpty();
    }
    
    @Test
    public void bodySearchShouldBeCaseInsensitive() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains(RHUBARD));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void bodySearchNotMatchPhraseOnlyInFrom() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains(FROM_ADDRESS));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).isEmpty();
    }

    @Test
    public void bodySearchShouldNotMatchPhraseOnlyInSubject() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains(SUBJECT_PART));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).isEmpty();
    }

    @Test
    public void textSearchShouldMatchPhraseInBody() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains(CUSTARD));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void textSearchShouldNotAbsentMatchPhraseInBody() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains(CUSTARD + CUSTARD));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).isEmpty();
    }

    @Test
    public void textSearchMatchShouldBeCaseInsensitive() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains(RHUBARD.toLowerCase(Locale.US)));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void addressSearchShouldMatchToFullAddress() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.address(AddressType.To,FROM_ADDRESS));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void addressSearchShouldMatchToDisplayName() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.address(AddressType.To,"Harry"));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    public void addressSearchShouldMatchToEmail() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.address(AddressType.To,"Harry@example.org"));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    public void addressSearchShouldMatchFrom() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.address(AddressType.From,"ser-from@domain.or"));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }

    @Test
    public void textSearchShouldMatchPhraseOnlyInToHeader() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains(FROM_ADDRESS));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    public void textSearchShouldMatchPhraseOnlyInSubjectHeader() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains(SUBJECT_PART));
        Iterator<MessageUid> result = index.search(session, mailbox3, query);
        assertThat(result).containsExactly(uid5);
    }
    
    @Test
    public void searchAllShouldMatchAllMailboxEmails() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        Iterator<MessageUid> result = index.search(session, mailbox2, query);
        assertThat(result).containsExactly(uid2);
    }

    @Test
    public void searchBodyInAllMailboxesShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains("My Body"));
        Map<MailboxId, Collection<MessageUid>> result = index.search(session, MultimailboxesSearchQuery.from(query).build());
        assertThat(result).hasSize(2);
        assertThat(result.get(mailbox.id)).containsExactly(uid1);
        assertThat(result.get(mailbox2.id)).containsExactly(uid2);
    }

    @Test
    public void searchBodyInSpecificMailboxesShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains("My Body"));
        Map<MailboxId, Collection<MessageUid>> result = index.search(session, 
                MultimailboxesSearchQuery.from(query).inMailboxes(mailbox.id, mailbox3.id).build());
        assertThat(result).hasSize(1);
        assertThat(result.get(mailbox.id)).containsExactly(uid1);
    }


    @Test
    public void searchAllShouldMatchAllUserEmails() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        Map<MailboxId, Collection<MessageUid>> result = index.search(session, MultimailboxesSearchQuery.from(query).build());
        assertThat(result).hasSize(3);
    }
    
    @Test
    public void flagSearchShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.flagIsSet(Flag.DELETED));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4);
    }
    
    @Test
    public void bodySearchShouldMatchSeveralEmails() throws Exception {    
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.bodyContains("body"));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void textSearchShouldMatchSeveralEmails() throws Exception {    
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.mailContains("body"));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void headerSearchShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.headerContains("Subject", "test"));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid4);
    }
    
    @Test
    public void headerExistsShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.headerExists("Subject"));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid4);
    }
    
    @Test
    public void flagUnsetShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.flagIsUnSet(Flag.DRAFT));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void internalDateBeforeShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        query.andCriteria(SearchQuery.internalDateBefore(cal.getTime(), DateResolution.Day));
        
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3);
    }
    
    
    @Test
    public void internalDateAfterShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        query.andCriteria(SearchQuery.internalDateAfter(cal.getTime(), DateResolution.Day));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4);
    }
    
    
    
    @Test
    public void internalDateOnShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        query.andCriteria(SearchQuery.internalDateOn(cal.getTime(), DateResolution.Day));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    public void uidSearchShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        query.andCriteria(SearchQuery.uid(new SearchQuery.UidRange[] {new SearchQuery.UidRange(uid1)}));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    public void uidRangeSearchShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        query.andCriteria(SearchQuery.uid(new SearchQuery.UidRange[] {new SearchQuery.UidRange(uid1), new SearchQuery.UidRange(uid3,uid4)}));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void sizeEqualsShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.sizeEquals(200));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1);
    }
    
    @Test
    public void sizeLessThanShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.sizeLessThan(200));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4);
    }
    
    @Test
    public void sizeGreaterThanShouldMatch() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.sizeGreaterThan(6));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void uidShouldBeSorted() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void uidReverseSortShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.Uid, true)));
        query.andCriteria(SearchQuery.all());
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4, uid3, uid1);
    }
    
    @Test
    public void sortOnSentDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.SentDate, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    public void reverseSortOnSentDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.SentDate, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid4, uid3);
    }

    @Test
    public void sortOnSubjectShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.BaseSubject, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    public void reverseSortOnSubjectShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.BaseSubject, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    public void sortOnMailboxFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxFrom, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    public void reverseSortOnMailboxFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxFrom, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid4, uid3);
    }
    
    @Test
    public void sortOnMailboxCCShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxCc, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void reverseSortOnMailboxCCShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxCc, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    public void sortOnMailboxToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxTo, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    public void reverseSortOnMailboxToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.MailboxTo, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    public void sortOnDisplayToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.DisplayTo, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    public void reverseSortOnDisplayToShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.DisplayTo, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    public void sortOnDisplayFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.DisplayFrom, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    public void reverseSortOnDisplayFromShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.DisplayFrom, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid4, uid3);
    }
    
    @Test
    public void sortOnArrivalDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.Arrival, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid1, uid4);
    }
    
    @Test
    public void reverseSortOnArrivalDateShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.Arrival, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid4, uid1, uid3);
    }
    
    @Test
    public void sortOnSizeShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.Size, false)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4, uid1);
    }
    
    @Test
    public void reverseSortOnSizeShouldReturnWellOrderedResults() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.all());
        query.setSorts(Arrays.asList(new SearchQuery.Sort(SortClause.Size, true)));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid1, uid3, uid4);
    }
    
    @Test
    public void notOperatorShouldReverseMatching() throws Exception {
        SearchQuery query = new SearchQuery();
        query.andCriteria(SearchQuery.not(SearchQuery.uid(new SearchQuery.UidRange[] { new SearchQuery.UidRange(uid1)})));
        Iterator<MessageUid> result = index.search(session, mailbox, query);
        assertThat(result).containsExactly(uid3, uid4);
    }
    
    private final class SimpleMailbox implements Mailbox {
        private final TestId id;

        public SimpleMailbox(long id) {
        	this.id = TestId.of(id);
        }

        public void setMailboxId(MailboxId id) {
        }

        public TestId getMailboxId() {
            return id;
        }

        public String getNamespace() {
            throw new UnsupportedOperationException("Not supported");
        }

        public void setNamespace(String namespace) {
            throw new UnsupportedOperationException("Not supported");
        }

        public String getUser() {
            throw new UnsupportedOperationException("Not supported");
        }

        public void setUser(String user) {
            throw new UnsupportedOperationException("Not supported");
        }

        public String getName() {
            return id.serialize();
        }

        public void setName(String name) {
            throw new UnsupportedOperationException("Not supported");

        }

        public long getUidValidity() {
            return 0;
        }

        @Override
        public MailboxACL getACL() {
            return SimpleMailboxACL.OWNER_FULL_ACL;
        }

        @Override
        public void setACL(MailboxACL acl) {
            throw new UnsupportedOperationException("Not supported");
        }


    }
}
