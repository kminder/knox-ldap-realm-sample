/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.minder.knox.gateway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.AuthenticationException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapContext;

import org.apache.hadoop.gateway.shirorealm.KnoxLdapRealm;
import org.apache.log4j.Logger;
import org.apache.shiro.realm.ldap.LdapUtils;

public class EyKnoxLdapRealm extends KnoxLdapRealm {

  private static Logger LOG = Logger.getLogger( EyKnoxLdapRealm.class );

  private static Pattern TEMPLATE_PATTERN = Pattern.compile( "\\{(\\d+?)\\}" );

  private final static SearchControls SUBTREE_SCOPE = new SearchControls();
  static {
    SUBTREE_SCOPE.setSearchScope( SearchControls.SUBTREE_SCOPE );
  }

  private String principalRegex = "(.*)";
  private Pattern principalPattern = null;
  private String userDnTemplate = null;
  private String userSearchFilter = null;

  public String getPrincipalRegex() {
    return principalRegex;
  }

  public void setPrincipalRegex( String regex ) {
    principalRegex = regex == null ? null : regex.trim();
    if( principalRegex != null ) {
      principalPattern = Pattern.compile( principalRegex );
    }
  }

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter( final String userSearchFilter ) {
    this.userSearchFilter = userSearchFilter == null ? null : userSearchFilter.trim();
  }

  public void setUserDnTemplate( final String template ) throws IllegalArgumentException {
    userDnTemplate = template;
    super.setUserDnTemplate( template );
  }

  private Matcher matchPrincipal( final String principal ) {
    Matcher matchedPrincipal = principalPattern.matcher( principal );
    if( !matchedPrincipal.matches() ) {
      throw new IllegalArgumentException( "Principal " + principal + " does not match " + principalRegex );
    }
    return matchedPrincipal;
  }

  protected String getUserDn( final String principal ) throws IllegalArgumentException, IllegalStateException {
    String userDn;
    Matcher matchedPrincipal = matchPrincipal( principal );
    String userSearchAttribute = getUserSearchAttributeName();

    // If not searching use the userDnTemplate and return.
    if ( (userSearchFilter == null || userSearchFilter.isEmpty()) &&
         (userSearchAttribute == null || userSearchAttribute.isEmpty()) ) {
      userDn = expandTemplate( userDnTemplate, matchedPrincipal );
      LOG.debug( "Computed user DN: " + userDn );
      return userDn;
    }

    // Create the searchBase and searchFilter from config.
    String searchBase = expandTemplate( getUserSearchBase(), matchedPrincipal );
    String searchFilter;
    if( userSearchFilter == null ) {
      searchFilter = String.format("(&(objectclass=%1$s)(%2$s=%3$s))",
          getUserObjectClass(), userSearchAttribute, principal);
    } else {
      searchFilter = expandTemplate( userSearchFilter, matchedPrincipal );
    }

    // Search for userDn and return.
    LdapContext systemLdapCtx = null;
    NamingEnumeration<SearchResult> searchResultEnum = null;
    try {
      systemLdapCtx = getContextFactory().getSystemLdapContext();
      LOG.debug( "Searching from " + searchBase + " with filter " + searchFilter );
      searchResultEnum = systemLdapCtx.search( searchBase, searchFilter, SUBTREE_SCOPE );
      // SearchResults contains all the entries in search scope
      if (searchResultEnum.hasMore()) {
        SearchResult searchResult = searchResultEnum.next();
        userDn = searchResult.getNameInNamespace();
        LOG.debug( "Found user DN: " + userDn );
        return userDn;
      } else {
        throw new IllegalArgumentException("Illegal principal name: " + principal);
      }
    } catch (AuthenticationException e) {
      throw new IllegalArgumentException("Illegal principal name: " + principal);
    } catch (NamingException e) {
      throw new IllegalArgumentException("Hit NamingException: " + e.getMessage());
    } finally {
      try {
        if (searchResultEnum != null) {
          searchResultEnum.close();
        }
      } catch (NamingException e) {
        // Ignore exception on close.
      }
      finally {
        LdapUtils.closeContext(systemLdapCtx);
      }
    }
  }

  private static final String expandTemplate( final String template, final Matcher input ) {
    String output = template;
    Matcher matcher = TEMPLATE_PATTERN.matcher( output );
    while( matcher.find() ) {
      String lookupStr = matcher.group( 1 );
      int lookupIndex = Integer.parseInt( lookupStr );
      String lookupValue = input.group( lookupIndex );
      output = matcher.replaceFirst( lookupValue == null ? "" : lookupValue );
      matcher = TEMPLATE_PATTERN.matcher( output );
    }
    return output;
  }

}
