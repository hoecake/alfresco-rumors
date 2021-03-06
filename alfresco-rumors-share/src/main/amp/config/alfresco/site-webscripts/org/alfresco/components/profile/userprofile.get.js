<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">
<import resource="classpath:/alfresco/site-webscripts/org/alfresco/components/upload/uploadable.lib.js">

/**
 * User Profile Component GET method
 */

function usernameFilter(element, index, array) { return element.contains(" username");}

function main()
{
   var profileId = page.url.templateArgs["userid"];
   if (profileId != null)
   {
      // load user details for the profile from the repo
      var userObj = user.getUser(profileId);
      if (userObj != null)
      {
         model.profile = userObj;
      }
      else
      {
         // fallback if unable to get user details
         model.profile = user.getUser(user.id);
      }
   }
   else
   {
      // if no profile specified, must be current user which will allow editing
      model.profile = user.getUser(user.id);
   }
   
   var xmppUsername;
   
   if (model.profile.toString().contains(" username"))
   {
	   xmppUsername = model.profile.toString().split(',').filter(usernameFilter).toString().split('=')[1];
	   model.username = xmppUsername;
   }
   
   // convert biography text to use <br/> line breaks
   var bio = model.profile.biography;
   if (bio != null)
   {
      model.biohtml = stringUtils.replaceLineBreaks(bio);
   }
   
   // editable if request profile is for the current user
   model.isEditable = (profileId == null || profileId == user.name);
   
   // add follow/unfollow buttons if request profile is not for the current user
   if (!model.isEditable)
   {
      var params = new Array(1);
      params.push(page.url.templateArgs["userid"]);
      
      var connector = remote.connect("alfresco");
      var result = connector.post("/api/subscriptions/" + encodeURIComponent(user.name) + "/follows",
                                  jsonUtils.toJSONString(params),
                                  "application/json");
      if (result.status == 200)
      {
         model.follows = eval('(' + result + ')')[0][page.url.templateArgs["userid"]];
      }
   }
   
   // Widget instantiation metadata...
   var userProfile = {
      id : "UserProfile", 
      name : "Alfresco.UserProfile",
      options : {
         userId : user.name,
         profile : {
            isEditable : model.isEditable,
            name : (model.profile.name != null) ? model.profile.name : "",
            lastName : (model.profile.lastName != null) ? model.profile.lastName : "",
            firstName : (model.profile.firstName != null) ? model.profile.firstName : "",
            jobtitle : (model.profile.jobTitle != null) ? model.profile.jobTitle : "",
            location : (model.profile.location != null) ? model.profile.location : "",
            bio : (model.profile.biography != null) ? model.profile.biography : "",
            telephone : (model.profile.telephone != null) ? model.profile.telephone : "",
            mobile : (model.profile.mobilePhone != null) ? model.profile.mobilePhone : "",
            email : (model.profile.email != null) ? model.profile.email : "",
            skype : (model.profile.skype != null) ? model.profile.skype : "",
            instantmsg : (model.profile.instantMsg != null) ? model.profile.instantMsg : "",
            googleusername : (model.profile.googleUsername != null) ? model.profile.googleUsername : "",
            organization : (model.profile.organization != null) ? model.profile.organization : "",
            companyaddress1 : (model.profile.companyAddress1 != null) ? model.profile.companyAddress1 : "",
            companyaddress2 : (model.profile.companyAddress2 != null) ? model.profile.companyAddress2 : "",
            companyaddress3 : (model.profile.companyAddress3 != null) ? model.profile.companyAddress3 : "",
            companypostcode : (model.profile.companyPostcode != null) ? model.profile.companyPostcode : "",
            companytelephone : (model.profile.companyTelephone != null) ? model.profile.companyTelephone : "",
            companyfax : (model.profile.companyFax != null) ? model.profile.companyFax : "",
            companyemail : (model.profile.companyEmail != null) ? model.profile.companyEmail : "",
            username : (xmppUsername != null) ? xmppUsername : ""
         }
      }
   };
   model.widgets = [userProfile];
}

main();

