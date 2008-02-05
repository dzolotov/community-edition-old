/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.web.bean.users;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.event.ActionEvent;
import javax.faces.validator.ValidatorException;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ApplicationModel;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.usage.ContentUsageService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.web.app.Application;
import org.alfresco.web.app.context.UIContextService;
import org.alfresco.web.bean.repository.Repository;
import org.alfresco.web.bean.spaces.CreateSpaceWizard;
import org.alfresco.web.bean.wizard.BaseWizardBean;
import org.alfresco.web.config.ClientConfigElement;
import org.alfresco.web.ui.common.Utils;
import org.apache.log4j.Logger;

/**
 * @author Kevin Roast
 */
public class CreateUserWizard extends BaseWizardBean
{
    private static Logger logger = Logger.getLogger(CreateUserWizard.class);
    protected static final String ERROR = "error_person";

    protected static final String QUOTA_UNITS_KB = "kilobyte";
    protected static final String QUOTA_UNITS_MB = "megabyte";
    protected static final String QUOTA_UNITS_GB = "gigabyte";
    
    /** form variables */
    protected String firstName = null;
    protected String lastName = null;
    protected String userName = null;
    protected String password = null;
    protected String confirm = null;
    protected String email = null;
    protected String companyId = null;
    protected String homeSpaceName = "";
    protected NodeRef homeSpaceLocation = null;
    protected String presenceProvider = null;
    protected String presenceUsername = null;
    protected String organisation = null;
    protected String jobtitle = null;
    protected String location = null;
    
    protected Long sizeQuota = null; // null is also equivalent to -1 (ie. no quota limit set)
    protected String sizeQuotaUnits = null;

    /** AuthenticationService bean reference */
    private AuthenticationService authenticationService;

    /** PersonService bean reference */
    private PersonService personService;
    
    /** TenantService bean reference */
    private TenantService tenantService;

    /** PermissionService bean reference */
    private PermissionService permissionService;

    /** OwnableService bean reference */
    private OwnableService ownableService;
    
    /** ContentUsageService bean reference */
    private ContentUsageService contentUsageService;
    

    /** ref to the company home space folder */
    private NodeRef companyHomeSpaceRef = null;

    /** ref to the default home location */
    private NodeRef defaultHomeSpaceRef;

    /**
     * @param authenticationService The AuthenticationService to set.
     */
    public void setAuthenticationService(AuthenticationService authenticationService)
    {
        this.authenticationService = authenticationService;
    }

    /**
     * @param personService The person service.
     */
    public void setPersonService(PersonService personService)
    {
        this.personService = personService;
    }
    
    /**
     * @param tenantService         The tenantService to set.
     */
    public void setTenantService(TenantService tenantService)
    {
       this.tenantService = tenantService;
    }

    /**
     * @param permissionService The PermissionService to set.
     */
    public void setPermissionService(PermissionService permissionService)
    {
        this.permissionService = permissionService;
    }

    /**
     * @param ownableService The ownableService to set.
     */
    public void setOwnableService(OwnableService ownableService)
    {
        this.ownableService = ownableService;
    }
   
    /**
     * @param contentUsageService The contentUsageService to set.
     */
    public void setContentUsageService(ContentUsageService contentUsageService)
    {
        this.contentUsageService = contentUsageService;
    }

    /**
     * Initialises the wizard
     */
    @Override
    public void init(Map<String, String> arg0)
    {
        super.init(arg0);

        // reset all variables
        this.firstName = "";
        this.lastName = "";
        this.userName = "";
        this.password = "";
        this.confirm = "";
        this.email = "";
        this.companyId = "";
        this.homeSpaceName = "";
        this.homeSpaceLocation = getDefaultHomeSpace();
        this.presenceProvider = "";
        this.presenceUsername = "";
        this.organisation = "";
        this.jobtitle = "";
        this.location = "";
        
        this.sizeQuota = null;
        this.sizeQuotaUnits = "";
    }

    /**
     * @return Returns the summary data for the wizard.
     */
    public String getSummary()
    {
        if (tenantService.isEnabled())
        {      
        	try
        	{
        		checkTenantUserName();
        	}
        	catch (Exception e)
        	{
        		// TODO - ignore for now, but ideally should handle earlier
        	}
        }
        
        ResourceBundle bundle = Application.getBundle(FacesContext.getCurrentInstance());
        
        String homeSpaceLabel = this.homeSpaceName;
        if (this.homeSpaceName.length() == 0 && this.homeSpaceLocation != null)
        {
            homeSpaceLabel = Repository.getNameForNode(this.nodeService, this.homeSpaceLocation);
        }
        
        String quotaLabel = "";
        if (this.sizeQuota != null && this.sizeQuota != -1L)
        {
            quotaLabel = Long.toString(this.sizeQuota) + bundle.getString(this.sizeQuotaUnits);
        }
        
        String presenceLabel = "";
        if (this.presenceProvider != null && this.presenceProvider.length() != 0)
        {
            presenceLabel = this.presenceUsername + " (" + presenceProvider + ")";
        }

        return buildSummary(
                new String[] {
                    bundle.getString("name"), bundle.getString("username"),
                    bundle.getString("password"), bundle.getString("homespace"),
                    bundle.getString("email"), bundle.getString("user_organization"),
                    bundle.getString("user_jobtitle"), bundle.getString("user_location"),
                    bundle.getString("presence_username"), bundle.getString("quota")},
                new String[] {
                    this.firstName + " " + this.lastName, this.userName,
                    "********", homeSpaceLabel,
                    this.email, this.organisation,
                    this.jobtitle, this.location,
                    presenceLabel, quotaLabel});
    }
    
    /**
     * Init the users screen
     */
    public void setupUsers(ActionEvent event)
    {
       invalidateUserList();
    }

    /**
     * @return Returns the companyId.
     */
    public String getCompanyId()
    {
        return this.companyId;
    }

    /**
     * @param companyId The companyId to set.
     */
    public void setCompanyId(String companyId)
    {
        this.companyId = companyId;
    }

    /**
     * @return Returns the presenceProvider.
     */
    public String getPresenceProvider()
    {
       return this.presenceProvider;
    }

    /**
     * @param presenceProvider
     *            The presenceProvider to set.
     */
    public void setPresenceProvider(String presenceProvider)
    {
       this.presenceProvider = presenceProvider;
    }

    /**
     * @return Returns the presenceUsername.
     */
    public String getPresenceUsername()
    {
       return this.presenceUsername;
    }

    /**
     * @param presenceUsername
     *            The presenceUsername to set.
     */
    public void setPresenceUsername(String presenceUsername)
    {
       this.presenceUsername = presenceUsername;
    }

    /**
     * @return Returns the email.
     */
    public String getEmail()
    {
        return this.email;
    }

    /**
     * @param email The email to set.
     */
    public void setEmail(String email)
    {
        this.email = email;
    }

    /**
     * @return Returns the firstName.
     */
    public String getFirstName()
    {
        return this.firstName;
    }

    /**
     * @param firstName The firstName to set.
     */
    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    /**
     * @return Returns the homeSpaceLocation.
     */
    public NodeRef getHomeSpaceLocation()
    {
        return this.homeSpaceLocation;
    }

    /**
     * @param homeSpaceLocation The homeSpaceLocation to set.
     */
    public void setHomeSpaceLocation(NodeRef homeSpaceLocation)
    {
        this.homeSpaceLocation = homeSpaceLocation;
    }

    /**
     * @return Returns the homeSpaceName.
     */
    public String getHomeSpaceName()
    {
        return this.homeSpaceName;
    }

    /**
     * @param homeSpaceName The homeSpaceName to set.
     */
    public void setHomeSpaceName(String homeSpaceName)
    {
        this.homeSpaceName = homeSpaceName;
    }

    /**
     * @return Returns the lastName.
     */
    public String getLastName()
    {
        return this.lastName;
    }

    /**
     * @param lastName The lastName to set.
     */
    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    /**
     * @return Returns the userName.
     */
    public String getUserName()
    {
        return this.userName;
    }

    /**
     * @param userName The userName to set.
     */
    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    /**
     * @return Returns the password.
     */
    public String getPassword()
    {
        return this.password;
    }

    /**
     * @param password The password to set.
     */
    public void setPassword(String password)
    {
        this.password = password;
    }

    /**
     * @return Returns the confirm password.
     */
    public String getConfirm()
    {
        return this.confirm;
    }

    /**
     * @param confirm The confirm password to set.
     */
    public void setConfirm(String confirm)
    {
        this.confirm = confirm;
    }
    
    /**
     * @return the jobtitle
     */
    public String getJobtitle()
    {
        return this.jobtitle;
    }

    /**
     * @param jobtitle the jobtitle to set
     */
    public void setJobtitle(String jobtitle)
    {
        this.jobtitle = jobtitle;
    }

    /**
     * @return the location
     */
    public String getLocation()
    {
        return this.location;
    }

    /**
     * @param location the location to set
     */
    public void setLocation(String location)
    {
        this.location = location;
    }

    /**
     * @return the organisation
     */
    public String getOrganization()
    {
        return this.organisation;
    }

    /**
     * @param organisation the organisation to set
     */
    public void setOrganization(String organisation)
    {
        this.organisation = organisation;
    }

    public Long getSizeQuota()
    {
       return sizeQuota;
    }

    public void setSizeQuota(Long sizeQuota)
    {
       this.sizeQuota = sizeQuota;
    }

    public String getSizeQuotaUnits()
    {
       return sizeQuotaUnits;
    }

    public void setSizeQuotaUnits(String sizeQuotaUnits)
    {
       this.sizeQuotaUnits = sizeQuotaUnits;
    }

    // ------------------------------------------------------------------------------
    // Validator methods

    /**
     * Validate password field data is acceptable
     */
    public void validatePassword(FacesContext context, UIComponent component, Object value) throws ValidatorException
    {
        String pass = (String) value;
        if (pass.length() < 5 || pass.length() > 12)
        {
            String err = "Password must be between 5 and 12 characters in length.";
            throw new ValidatorException(new FacesMessage(err));
        }

        for (int i = 0; i < pass.length(); i++)
        {
            if (Character.isLetterOrDigit(pass.charAt(i)) == false)
            {
                String err = "Password can only contain characters or digits.";
                throw new ValidatorException(new FacesMessage(err));
            }
        }
    }

    /**
     * Validate Username field data is acceptable
     */
    public void validateUsername(FacesContext context, UIComponent component, Object value) throws ValidatorException
    {
        String pass = (String) value;
        if (pass.length() < 5 || pass.length() > 12)
        {
            String err = "Username must be between 5 and 12 characters in length.";
            throw new ValidatorException(new FacesMessage(err));
        }

        for (int i = 0; i < pass.length(); i++)
        {
            if (Character.isLetterOrDigit(pass.charAt(i)) == false)
            {
                String err = "Username can only contain characters or digits.";
                throw new ValidatorException(new FacesMessage(err));
            }
        }
    }

    // ------------------------------------------------------------------------------
    // Helper methods

    /**
     * Helper to return the company home space
     * 
     * @return company home space NodeRef
     */
    protected NodeRef getCompanyHomeSpace()
    {
        if (this.companyHomeSpaceRef == null)
        {
            String companyXPath = Application.getRootPath(FacesContext.getCurrentInstance());

            NodeRef rootNodeRef = this.nodeService.getRootNode(Repository.getStoreRef());
            List<NodeRef> nodes = this.searchService.selectNodes(rootNodeRef, companyXPath, null, this.namespaceService, false);

            if (nodes.size() == 0)
            {
                throw new IllegalStateException("Unable to find company home space path: " + companyXPath);
            }

            this.companyHomeSpaceRef = nodes.get(0);
        }

        return this.companyHomeSpaceRef;
    }

    protected NodeRef getDefaultHomeSpace()
    {
        if ((this.defaultHomeSpaceRef == null) || !nodeService.exists(this.defaultHomeSpaceRef))
        {
            String defaultHomeSpacePath = Application.getClientConfig(FacesContext.getCurrentInstance()).getDefaultHomeSpacePath();

            NodeRef rootNodeRef = this.nodeService.getRootNode(Repository.getStoreRef());
            List<NodeRef> nodes = this.searchService.selectNodes(rootNodeRef, defaultHomeSpacePath, null, this.namespaceService, false);

            if (nodes.size() == 0)
            {
                return getCompanyHomeSpace();
            }

            this.defaultHomeSpaceRef = nodes.get(0);
        }

        return this.defaultHomeSpaceRef;
    }

    /**
     * Create the specified home space if it does not exist, and return the ID
     * 
     * @param locationId Parent location
     * @param spaceName Home space to create, can be null to simply return the parent
     * @param error True to throw an error if the space already exists, else ignore and return
     * @return ID of the home space
     */
    protected NodeRef createHomeSpace(String locationId, String spaceName, boolean error)
    {
        NodeRef homeSpaceNodeRef = null;
        if (spaceName != null && spaceName.length() != 0)
        {
            NodeRef parentRef = new NodeRef(Repository.getStoreRef(), locationId);

            // check for existance of home space with same name - return immediately
            // if it exists or throw an exception an give user chance to enter another name
            // TODO: this might be better replaced with an XPath query!
            List<ChildAssociationRef> children = this.nodeService.getChildAssocs(parentRef);
            for (ChildAssociationRef ref : children)
            {
                String childNodeName = (String) this.nodeService.getProperty(ref.getChildRef(), ContentModel.PROP_NAME);
                if (spaceName.equals(childNodeName))
                {
                    if (error)
                    {
                        throw new AlfrescoRuntimeException("A Home Space with the same name already exists.");
                    }
                    else
                    {
                        return ref.getChildRef();
                    }
                }
            }

            // space does not exist already, create a new Space under it with
            // the specified name
            String qname = QName.createValidLocalName(spaceName);
            ChildAssociationRef assocRef = this.nodeService.createNode(parentRef, ContentModel.ASSOC_CONTAINS, QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI, qname),
                    ContentModel.TYPE_FOLDER);

            NodeRef nodeRef = assocRef.getChildRef();

            // set the name property on the node
            this.nodeService.setProperty(nodeRef, ContentModel.PROP_NAME, spaceName);

            if (logger.isDebugEnabled())
                logger.debug("Created Home Space for with name: " + spaceName);

            // apply the uifacets aspect - icon, title and description props
            Map<QName, Serializable> uiFacetsProps = new HashMap<QName, Serializable>(3);
            uiFacetsProps.put(ApplicationModel.PROP_ICON, CreateSpaceWizard.DEFAULT_SPACE_ICON_NAME);
            uiFacetsProps.put(ContentModel.PROP_TITLE, spaceName);
            this.nodeService.addAspect(nodeRef, ApplicationModel.ASPECT_UIFACETS, uiFacetsProps);

            setupHomeSpacePermissions(nodeRef);

            // return the ID of the created space
            homeSpaceNodeRef = nodeRef;
        }

        return homeSpaceNodeRef;
    }

    /**
     * Setup the default permissions for this and other users on the Home Space
     * 
     * @param homeSpaceRef Home Space reference
     */
    private void setupHomeSpacePermissions(NodeRef homeSpaceRef)
    {
        // Admin Authority has full permissions by default (automatic - set in the permission config)
        // give full permissions to the new user
        this.permissionService.setPermission(homeSpaceRef, this.userName, permissionService.getAllPermission(), true);

        // by default other users will only have GUEST access to the space contents
        // or whatever is configured as the default in the web-client-xml config
        String permission = getDefaultPermission();
        if (permission != null && permission.length() != 0)
        {
            this.permissionService.setPermission(homeSpaceRef, permissionService.getAllAuthorities(), permission, true);
        }

        // the new user is the OWNER of their own space and always has full permissions
        this.ownableService.setOwner(homeSpaceRef, this.userName);
        this.permissionService.setPermission(homeSpaceRef, permissionService.getOwnerAuthority(), permissionService.getAllPermission(), true);

        // now detach (if we did this first we could not set any permissions!)
        this.permissionService.setInheritParentPermissions(homeSpaceRef, false);
    }

    /**
     * @return default permission string to set for other users for a new Home Space
     */
    private String getDefaultPermission()
    {
        ClientConfigElement config = Application.getClientConfig(FacesContext.getCurrentInstance());
        return config.getHomeSpacePermission();
    }

    private void invalidateUserList()
    {
        UIContextService.getInstance(FacesContext.getCurrentInstance()).notifyBeans();
    }

    @Override
    protected String finishImpl(FacesContext context, String outcome) throws Exception
    {
        // TODO: implement create new Person object from specified details
        try
        {
            if (this.password.equals(this.confirm))
            {
                if (tenantService.isEnabled())
                {         
                	checkTenantUserName();
                }
            	
                // create properties for Person type from submitted Form data
                Map<QName, Serializable> props = new HashMap<QName, Serializable>(7, 1.0f);
                props.put(ContentModel.PROP_USERNAME, this.userName);
                props.put(ContentModel.PROP_FIRSTNAME, this.firstName);
                props.put(ContentModel.PROP_LASTNAME, this.lastName);
                NodeRef homeSpaceNodeRef;
                if (this.homeSpaceLocation != null && this.homeSpaceName.length() != 0)
                {
                    // create new
                    homeSpaceNodeRef = createHomeSpace(this.homeSpaceLocation.getId(), this.homeSpaceName, true);
                }
                else if (this.homeSpaceLocation != null)
                {
                    // set to existing
                    homeSpaceNodeRef = homeSpaceLocation;
                    setupHomeSpacePermissions(homeSpaceNodeRef);
                }
                else
                {
                    // default to Company Home
                    homeSpaceNodeRef = getCompanyHomeSpace();
                }
                props.put(ContentModel.PROP_HOMEFOLDER, homeSpaceNodeRef);
                props.put(ContentModel.PROP_EMAIL, this.email);
                props.put(ContentModel.PROP_ORGID, this.companyId);
                props.put(ContentModel.PROP_ORGANIZATION, this.organisation);
                props.put(ContentModel.PROP_JOBTITLE, this.jobtitle);
                props.put(ContentModel.PROP_LOCATION, this.location);
                props.put(ContentModel.PROP_PRESENCEPROVIDER, this.presenceProvider);
                props.put(ContentModel.PROP_PRESENCEUSERNAME, this.presenceUsername);

                // create the node to represent the Person
                NodeRef newPerson = this.personService.createPerson(props);

                // ensure the user can access their own Person object
                this.permissionService.setPermission(newPerson, this.userName, permissionService.getAllPermission(), true);

                if (logger.isDebugEnabled())
                    logger.debug("Created Person node for username: " + this.userName);

                // create the ACEGI Authentication instance for the new user
                this.authenticationService.createAuthentication(this.userName, this.password.toCharArray());

                if (logger.isDebugEnabled())
                    logger.debug("Created User Authentication instance for username: " + this.userName);
                
                if ((this.sizeQuota != null) && (this.sizeQuota < 0L))
                {
                    Utils.addErrorMessage(MessageFormat.format(Application.getMessage(context, UsersDialog.ERROR_NEGATIVE_QUOTA), this.sizeQuota));
                    outcome = null;
                }
                else
                {
                	putSizeQuotaProperty(this.userName, this.sizeQuota, this.sizeQuotaUnits);
                }
            }
            else
            {
                Utils.addErrorMessage(Application.getMessage(context, UsersDialog.ERROR_PASSWORD_MATCH));
                outcome = null;
            }
            invalidateUserList();
        }
        catch (Throwable e)
        {
            Utils.addErrorMessage(MessageFormat.format(Application.getMessage(FacesContext.getCurrentInstance(), ERROR), e.getMessage()), e);
            outcome = null;
        }
        
        if (outcome == null) {
            this.isFinished = false;
        }
        
        return outcome;
    }

    @Override
    public boolean getFinishButtonDisabled()
    {
        if (this.firstName != null && this.lastName != null && this.email != null && this.firstName.length() > 0 && this.lastName.length() > 0 && this.email.length() > 0)
        {
            return false;
        }
        return true;
    }
    
    protected void putSizeQuotaProperty(String userName, Long quota, String quotaUnits)
    {
       if (quota != null)
       {
    	  if (quota >= 0L)
    	  {
    		  quota = convertToBytes(quota, quotaUnits);
    	  }
    	  else
    	  {
    		  // ignore negative quota
    		  return;
    	  }
       }
       
       this.contentUsageService.setUserQuota(userName, (quota == null ? -1 : quota));
    }
    
    protected long convertToBytes(long size, String units)
    {
       if (units != null)
       {
          if (units.equals(QUOTA_UNITS_KB))
          {
             size = size * 1024L;
          }
          else if (units.equals(QUOTA_UNITS_MB))
          {
             size = size * 1048576L;
          }
          else if (units.equals(QUOTA_UNITS_GB))
          {
             size = size * 1073741824L;
          }
       }
       return size;
    }
    
    protected Pair<Long, String> convertFromBytes(long size)
    {
       String units = null;
       if (size <= 0)
       {
          units = QUOTA_UNITS_GB;
       }
       else if (size < 999999)
       {
          size = (long)((double)size / 1024.0d);
          units = QUOTA_UNITS_KB;
       }
       else if (size < 999999999)
       {
          size = (long)((double)size / 1048576.0d);
          units = QUOTA_UNITS_MB;
       }
       else
       {
          size = (long)((double)size / 1073741824.0d);
          units = QUOTA_UNITS_GB;
       }
       return new Pair<Long, String>(size, units);
    }
    
    protected void checkTenantUserName()
    {
        String currentDomain = tenantService.getCurrentUserDomain();
        if (! currentDomain.equals(TenantService.DEFAULT_DOMAIN))
        {
            if (! tenantService.isTenantUser(this.userName))
            {
                // force domain onto the end of the username
                this.userName = tenantService.getDomainUser(this.userName, currentDomain);
                logger.warn("Added domain to username: " + this.userName);
            }
            else
            {
                try
                {                  
                    tenantService.checkDomainUser(this.userName);
                }
                catch (RuntimeException re)
                {
                    throw new AuthenticationException("User must belong to same domain as admin: " + currentDomain);
                }
            }
        } 
    }
}
