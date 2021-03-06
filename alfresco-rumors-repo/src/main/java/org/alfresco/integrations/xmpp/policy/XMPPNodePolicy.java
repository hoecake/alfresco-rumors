
package org.alfresco.integrations.xmpp.policy;


import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import org.alfresco.integrations.xmpp.XMPPModel;
import org.alfresco.integrations.xmpp.service.XMPPService;
import org.alfresco.model.ContentModel;
import org.alfresco.model.ForumModel;
import org.alfresco.repo.content.ContentServicePolicies.OnContentUpdatePolicy;
import org.alfresco.repo.forum.CommentService;
import org.alfresco.repo.node.NodeServicePolicies.BeforeDeleteNodePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnMoveNodePolicy;
import org.alfresco.repo.node.NodeServicePolicies.OnUpdatePropertiesPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class XMPPNodePolicy
    implements OnContentUpdatePolicy, OnUpdatePropertiesPolicy, OnMoveNodePolicy, BeforeDeleteNodePolicy
{
    private final static Log  log = LogFactory.getLog(XMPPNodePolicy.class);

    private PolicyComponent   policyComponent;

    private XMPPService       xmppService;
    private FileFolderService fileFolderService;
    private NodeService       nodeService;
    private PermissionService permissionService;
    private BehaviourFilter   policyFilter;
    private CommentService    commentService;
    private ContentService    contentService;


    public void setPolicyComponent(final PolicyComponent policyComponent)
    {
        this.policyComponent = policyComponent;
    }


    public void setXmppService(XMPPService xmppService)
    {
        this.xmppService = xmppService;
    }


    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }


    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }


    public void setPermissionService(PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }


    public void setPolicyFilter(BehaviourFilter policyFilter)
    {
        this.policyFilter = policyFilter;
    }


    public void setCommentService(CommentService commentService)
    {
        this.commentService = commentService;
    }


    public void setContentService(ContentService contentService)
    {
        this.contentService = contentService;
    }


    public void init()
    {
        policyComponent.bindClassBehaviour(OnContentUpdatePolicy.QNAME, XMPPModel.ASPECT_XMPP_NODE, new JavaBehaviour(this, "onContentUpdate", NotificationFrequency.FIRST_EVENT));
        policyComponent.bindClassBehaviour(OnUpdatePropertiesPolicy.QNAME, ContentModel.TYPE_CONTENT, new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.TRANSACTION_COMMIT));
        policyComponent.bindClassBehaviour(OnMoveNodePolicy.QNAME, XMPPModel.ASPECT_XMPP_NODE, new JavaBehaviour(this, "onMoveNode", NotificationFrequency.FIRST_EVENT));
        policyComponent.bindClassBehaviour(BeforeDeleteNodePolicy.QNAME, XMPPModel.ASPECT_XMPP_NODE, new JavaBehaviour(this, "beforeDeleteNode", NotificationFrequency.FIRST_EVENT));

        policyComponent.bindClassBehaviour(OnContentUpdatePolicy.QNAME, ForumModel.TYPE_POST, new JavaBehaviour(this, "onCommentPost", NotificationFrequency.EVERY_EVENT));
    }


    @Override
    public void onContentUpdate(NodeRef nodeRef, boolean newContent)
    {
        xmppService.broadcast(nodeRef, ((String)nodeService.getProperty(nodeRef, ContentModel.PROP_NAME))
                                         + " was updated by "
                                         + AuthenticationUtil.getFullyAuthenticatedUser()
                                         + (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_VERSIONABLE)
                                                                                                           ? ". Version "
                                                                                                             + nodeService.getProperty(nodeRef, ContentModel.PROP_VERSION_LABEL)
                                                                                                           : ""));
    }


    @Override
    public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after)
    {
        if (nodeService.exists(nodeRef))
        {
            if (nodeService.hasAspect(nodeRef, XMPPModel.ASPECT_XMPP_NODE))
            {
                String previousName = (String)before.get(ContentModel.PROP_NAME);
                String newName = (String)after.get(ContentModel.PROP_NAME);

                if (!previousName.equals(newName))
                {
                    xmppService.updateXMPPNode(nodeRef, after);
                    xmppService.updateXMPPUserRosterName(nodeRef);
                    xmppService.broadcast(nodeRef, previousName + " was changed to " + newName + " by "
                                                     + AuthenticationUtil.getFullyAuthenticatedUser());
                }
            }
        }
    }


    @Override
    public void onMoveNode(ChildAssociationRef oldChildAssocRef, ChildAssociationRef newChildAssocRef)
    {
        String fromPath = nodeService.getPath(oldChildAssocRef.getParentRef()).toDisplayPath(nodeService, permissionService);
        String toPath = nodeService.getPath(newChildAssocRef.getChildRef()).toDisplayPath(nodeService, permissionService);

        xmppService.broadcast(newChildAssocRef.getChildRef(), nodeService.getProperty(newChildAssocRef.getChildRef(), ContentModel.PROP_NAME)
                                                                + " was moved from " + fromPath + " to " + toPath);

    }


    @Override
    public void beforeDeleteNode(NodeRef nodeRef)
    {
        xmppService.broadcast(nodeRef, nodeService.getProperty(nodeRef, ContentModel.PROP_NAME) + " was deleted.");
        xmppService.deleteUser(nodeRef);
    }


    public void onCommentPost(NodeRef nodeRef, boolean newContent)
    {
        NodeRef ancestor = commentService.getDiscussableAncestor(nodeRef);

        if (nodeService.hasAspect(ancestor, XMPPModel.ASPECT_XMPP_NODE))
        {
            xmppService.broadcast(ancestor, getCommentModifier(nodeRef) + " said " + getComment(nodeRef), Arrays.asList(getCommentModifier(nodeRef)));
        }
    }


    private String getComment(NodeRef nodeRef)
    {
        ContentReader contentReader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        return contentReader.getContentString();
    }


    private String getCommentModifier(NodeRef nodeRef)
    {
        return (String)nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIER);
    }


}
