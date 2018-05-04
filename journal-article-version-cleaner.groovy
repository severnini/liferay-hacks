/*******************************************************************************
*
* Script to clean JournalArticle (Web content) versions
*
* Author: Luiz Fernando Severnini
* Date: 2018-04-19
* Java version: 8
* Tested on: Liferay 6.2
*
*******************************************************************************/

import com.liferay.portal.ModelListenerException;
import com.liferay.portal.kernel.dao.orm.DynamicQuery;
import com.liferay.portal.kernel.dao.orm.DynamicQueryFactoryUtil;
import com.liferay.portal.kernel.dao.orm.OrderFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionFactoryUtil;
import com.liferay.portal.kernel.dao.orm.ProjectionList;
import com.liferay.portal.kernel.dao.orm.PropertyFactoryUtil;
import com.liferay.portal.kernel.dao.orm.QueryUtil;
import com.liferay.portal.kernel.dao.orm.RestrictionsFactoryUtil;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.workflow.WorkflowConstants;
import com.liferay.portal.model.BaseModelListener;
import com.liferay.portlet.journal.model.JournalArticle;
import com.liferay.portlet.journal.service.JournalArticleLocalServiceUtil;
import com.liferay.portlet.journal.util.comparator.ArticleVersionComparator;

import java.util.List;

import org.apache.commons.lang.time.StopWatch;

def runJournalArticleVersionCleaner(int maxVersions, boolean commitDeletion, int[] status) {

    Log log = LogFactoryUtil.getLog("JournalArticleVersionCleaner");

    StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    long deleteCount = 0;

    int buffer = 1000;
    int start = 0;
    int end = buffer;

    ArticleVersionComparator articleVersionComparator = new ArticleVersionComparator(true);

    DynamicQuery dynamicQueryRPK = DynamicQueryFactoryUtil.forClass(JournalArticle.class, "articles", PortalClassLoaderUtil.getClassLoader());

    ProjectionList projectionList = ProjectionFactoryUtil.projectionList();
    projectionList.add(ProjectionFactoryUtil.distinct(ProjectionFactoryUtil.property("resourcePrimKey")));
    projectionList.add(ProjectionFactoryUtil.property("articleId"));
    projectionList.add(ProjectionFactoryUtil.property("groupId"));

    dynamicQueryRPK.setProjection(projectionList);
    dynamicQueryRPK.addOrder(OrderFactoryUtil.desc("resourcePrimKey"));

    try {
        List<Object[]> articles = JournalArticleLocalServiceUtil.dynamicQuery(dynamicQueryRPK, start, end);

        while(!articles.isEmpty()) {

            log.info("Processing from " + start + " to " + end);

            for (Object[] obj : articles) {
                DynamicQuery dynamicQuery = JournalArticleLocalServiceUtil.dynamicQuery();
                dynamicQuery.add(RestrictionsFactoryUtil.eq("articleId", obj[1].toString()));
                dynamicQuery.add(RestrictionsFactoryUtil.eq("groupId", Long.parseLong(obj[2].toString())));

                if (status.length > 0) {
                    dynamicQuery.add(PropertyFactoryUtil.forName("status").in(status));
                }

                try {
                    List<JournalArticle> list = JournalArticleLocalServiceUtil.dynamicQuery(dynamicQuery, QueryUtil.ALL_POS, QueryUtil.ALL_POS, articleVersionComparator);

                    if (list.size() > maxVersions) {
                        log.info("resourcePrimKey: " + obj[0].toString() + " - articleId: " + obj[1].toString() + " - groupId: " + obj[2].toString());

                        for (int i = 0; i < list.size() - maxVersions; i++) {
                            JournalArticle journalArticle = list.get(i);
                            log.info("Delete articleId: [" + journalArticle.getArticleId() + "] - version: [" + journalArticle.getVersion() + "] - title: [" + journalArticle.getTitleCurrentValue() + "] - commited: [" + commitDeletion + "]");
                            if (commitDeletion) {
                                JournalArticleLocalServiceUtil.deleteArticle(journalArticle);
                            }
                            deleteCount++;
                        }
                    }
                } catch (Exception e) {
                    //do nothing
                }
            }

            //Sleep 2 minutes each iteration
            /* try { TimeUnit.MINUTES.sleep(2); } catch (InterruptedException e) { System.err.println(e); } */

            start = end;
            end += buffer;

            articles = JournalArticleLocalServiceUtil.dynamicQuery(dynamicQueryRPK, start, end);
        }
    }
    catch (SystemException e) {
        log.error(e);
    }

    log.info("Deleted " + deleteCount + " versions - time: " + stopWatch.getTime() + " ms - commited: [" + commitDeletion + "]");

    out.println("Deleted " + deleteCount + " versions - time: " + stopWatch.getTime() + " ms - commited: [" + commitDeletion + "]");
}


//If true will commit the delete, false will only log content that can be deleted
boolean commitDeletion = false;

//How many versions will be kept
int maxVersions = 7;

//Status that will be considered during cleaning, if empty will remove any status
int[] status = [WorkflowConstants.STATUS_APPROVED];

//Run cleaner
runJournalArticleVersionCleaner(maxVersions, commitDeletion, status);
