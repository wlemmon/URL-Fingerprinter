# URL-Fingerprinter
An ant + Java build tool for URL Fingerprinting / forever caching static resources. Implements URL Fingerprinting as described by Google: 
<a href="https://developers.google.com/speed/docs/best-practices/caching" target="_blank">Performance Best Practices: Optimize Caching<a>

<br>
<b>Works on JS, CSS, and HTML.</b>


## Example
Turns this:
```html
<html>
	<head>
		<link rel="stylesheet" type="text/css" href="resources/css/mycss.css"/>
		<script type="text/javascript" src="myclasses.js"></script>
	</head>
	<body>
	</body>
</html>
```
into this:
```html
<html>
	<head>
		<link rel="stylesheet" type="text/css" href="resources/css/239rmfhr93483h883893892fh238904t-mycss.css"/>
		<script type="text/javascript" src="0keie84i3j4irkej43u4u4jgj4ui4i33-myclasses.js"></script>
	</head>
	<body>
	</body>
</html>
```

## Building
```sh
ant run-fullbuild
```

## Usage

After building, the directory "build" will contain a jar for classes and a jar for sources. Copy these jars into your BlueCloud webapp's lib/build and lib/src directories, respectively.
URL-Fingerprinter.jar
URL-Fingerprinter-src.jar

Copy all BCAnt lib/main files into your project's lib/build directory:
ant.jar
commons-io-2.4.jar
rhino1_7R4.jar

The tool inputs and outputs are defined as follows:<br>
Input: A Tomcat WAR's resources <br>
Output: Cacheable forever resources by browsers. An MD5 hash is put in the filenames and references. <br>

From your projects build.xml file, you can include any of the ant macros. The below example will locate the URL-Fingerprinter macro from the URL-Fingerprinter.jar file.

```xml
  <include>
		<javaresource name="URL-Fingerprinter.xml">
			<classpath location="${build.lib.dir}/URL-Fingerprinter.jar"/>
		</javaresource>
	</include>

```

Next, call the ant macro. Make sure to link it to any external dependencies, i.e., the jars you moved from URL-Fingerprinter's lib/main directory.

```xml
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
```

## Future changes:<br>
Contributors welcome.<br>
* Convert to maven project
* Support the editing of JSP and other reference containing resource types.



