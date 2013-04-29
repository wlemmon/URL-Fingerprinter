# URL-Fingerprinter


## Tool Function

Implements URL Fingerprinting as described here: <br>
Optimize caching: <br>
https://developers.google.com/speed/docs/best-practices/rules_intro <br>
<br>
Input: A Tomcat WAR's resources <br>
Output: Cacheable forever resources by browsers. An MD5 hash is put in the filenames and references. <br>
<br>
Current Extent of reference renaming: <br>
JS, CSS, HTML.
Please contribute.

## Tool Details
An ant tool + Java class

## Usage

DISTRIBUTING:
After building, the directory "build" will contain a jar for classes and a jar for sources. Copy these jars into your BlueCloud webapp's lib/build and lib/src directories, respectively.
URL-Fingerprinter.jar
URL-Fingerprinter-src.jar

Copy all BCAnt lib/main files into your project's lib/build directory:
ant.jar
commons-io-2.4.jar
rhino1_7R4.jar

From your projects build.xml file, you can include any of the ant macros. The below example will locate the URL-Fingerprinter macro from the URL-Fingerprinter.jar file.
  <include>
		<javaresource name="URL-Fingerprinter.xml">
			<classpath location="${build.lib.dir}/URL-Fingerprinter.jar"/>
		</javaresource>
	</include>



Next, call the ant macro. Make sure to link it to any external dependencies, i.e., the jars you moved from URL-Fingerprinter's lib/main directory.
	<URL-Fingerprintize
		war-temp-dir="${build.dir.webresources}"
		build-dir="${build.dir}"
		reference-file="${build.dir}/md5-map.properties"
		dependency-classpath="${build.lib.dir}/commons-io-2.4.jar;${build.lib.dir}/rhino1_7R4.jar;${build.lib.dir}/URL-Fingerprinter.jar;${build.lib.dir}/ant.jar;"
	>
		<hash-files>
			<fileset dir="${build.dir.webresources}" >
				<include name="**/*.css" />
				<include name="**/*.json" />
				<include name="**/*.js" />
				<include name="**/*.jpg" />
				<include name="**/*.gif" />
				<include name="**/*.png" />
				<include name="**/*.ico" />
				<exclude name="extjs/" />
			</fileset>
		</hash-files>
		<modify-files>
			<fileset dir="@{war-temp-dir}">
				<include name="**/*.js" />
				<include name="**/*.css" />
				<include name="**/*.html" />
				<exclude name="extjs/**/*" />
			</fileset>
		</modify-files>
	</URL-Fingerprintize>






