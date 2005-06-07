<?xml version="1.0"?>

<!--
  Copyright 1999-2004 The Apache Software Foundation

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!--
  To use, transform the gump.xml filewith this stylesheet, install the 
  Maven Artifact Ant task in your Ant lib dir, and run the generated file.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
 
  <xsl:output method="xml" indent="yes"/>

  <xsl:template match="/"><xsl:apply-templates select="module"/></xsl:template>

  <xsl:template match="module">
    <project name="ccocoon-classpath" 
             xmlns:artifact="antlib:org.apache.maven.artifact.ant" 
             default="classpath">
          
       <description>Autogenerated Ant build file that creates the classpath.</description>
	   <target name="classpath">
         <artifact:dependencies pathId="dependency.classpath">
	        <xsl:for-each select="project">
               <xsl:for-each select="depend | dependx">
               
	               <xsl:choose>
	               
	               	<xsl:when test="@locallib">
	               	 <xsl:comment>
	               	   <xsl:value-of select="@locallib" />
	               	 </xsl:comment>
	               	</xsl:when>
	               	
	               	<xsl:when test="@groupId and @artifactId and @version">
	                   <dependency groupId="{@groupId}" 
	                               artifactId="{@artifactId}" 
	                               version="{@version}"/>
	               	</xsl:when>
               	
	               	<xsl:otherwise>
	               	 <xsl:comment>Skipping <xsl:value-of select="@project" />
	               	 </xsl:comment>
	               	</xsl:otherwise>
	               	
	               </xsl:choose>

	           </xsl:for-each>
	       </xsl:for-each>
         </artifact:dependencies>
         
         
       <path id="local.classpath">
	        <xsl:for-each select="project">
               <xsl:for-each select="depend | dependx">
	              <xsl:if test="@locallib">
	              	 <path location="lib/{@locallib}"/>
                  </xsl:if>
	           </xsl:for-each>
	       </xsl:for-each>
       </path>
    
    
        <path id="classpath">
          <path refid="dependency.classpath"/>
          <path refid="local.classpath"/>
        </path>

         
	   </target>
	 </project>
  </xsl:template>


</xsl:stylesheet>
