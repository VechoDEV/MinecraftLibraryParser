package xyz.vecho.libraryparser;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Main extends JFrame implements ActionListener {

	static JComboBox box;
	static JButton downloadBtn;
	static File workDirectory;
	static File manifest;
	public Main() {
		JPanel panel = new JPanel(new GridLayout(4,4,4,4));
		this.setSize(350, 250);
		box = new JComboBox<String>(new String[] {});
		box.setSize(70, 50);
		downloadBtn = new JButton("Download lib");
		downloadBtn.setPreferredSize(new Dimension(70, 50));
		downloadBtn.addActionListener(this);
		panel.add(box);
		panel.add(downloadBtn);
		this.setContentPane(panel);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		this.setLocation(dim.width/2-this.getSize().width/2, dim.height/2-this.getSize().height/2);
		this.setVisible(true);
	}
	
	public static void main(String[] args) throws MalformedURLException, IOException {
		Main main = new Main();
		FrameLog framelog = new FrameLog();
		framelog.setPrints();
		System.out.println("Select a directory for workPath");
		System.out.println("This directory used to jsons and libs download");
		final JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		File workPathFile = null;
		int val = fc.showOpenDialog(null);
		if (val == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			if (file.isDirectory()) {
				workPathFile = file;
			} else {
				System.out.println("You must select a folder. Waiting for close the log area for exiting.");
				main.dispose();
				main = null;
			}
		} else {
			System.out.println("You must select a folder. Waiting for close the log area for exiting.");
			main.dispose();
			main = null;
		}
		if (main != null) {
			workDirectory = workPathFile;
			manifest = new File(workDirectory, "version_manifest_v2.json");
			String url = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
			long expected = ((HttpURLConnection)new URL(url).openConnection()).getContentLengthLong();
			if (!manifest.exists() || expected != manifest.length()) {
				InputStream stream = new URL(url).openStream();
				IOUtils.copy(stream, new FileOutputStream(manifest));
			}
			JsonObject obj = JsonParser.parseReader(new FileReader(manifest)).getAsJsonObject();
			JsonArray versions = obj.getAsJsonArray("versions");
			versions.forEach((action) -> {
				JsonObject version = action.getAsJsonObject();
				main.box.addItem(version.get("id").getAsString());
			});
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(downloadBtn)) {
			if (box.getSelectedItem() != null) {
				String id = (String) box.getSelectedItem();
				JsonObject obj;
				try {
					obj = JsonParser.parseReader(new FileReader(manifest)).getAsJsonObject();
					JsonArray versions = obj.getAsJsonArray("versions");
					versions.forEach((action) -> {
						JsonObject version = action.getAsJsonObject();
						if (version.get("id").getAsString().equals(id)) {
							try {
								File jsons = new File(workDirectory, "jsons");
								if (!jsons.exists()) jsons.mkdirs();
								File json = new File(jsons, id+".json");
								if (!json.exists()) IOUtils.copy(new URL(version.get("url").getAsString()).openStream(), new FileOutputStream(json));
								JsonObject libraries1 = JsonParser.parseReader(new FileReader(json)).getAsJsonObject();
								JsonArray libraries = libraries1.getAsJsonArray("libraries");
								File librariesDir = new File(workDirectory, "libraries");
								if (!librariesDir.exists()) librariesDir.mkdirs();
								libraries.forEach((action2) -> {
									JsonObject library = action2.getAsJsonObject();
									String name = library.get("name").getAsString();
									String[] mavenName = name.split(":");
									String[] mavenNameArtifact = mavenName[0].split("\\.");
									mavenName = (String[]) ArrayUtils.remove(mavenName, 0);
									String fullPath = StringUtils.join(mavenNameArtifact, File.separatorChar) + File.separatorChar + StringUtils.join(mavenName, File.separatorChar);
									File dir = new File(librariesDir, fullPath);
									File jar = new File(dir, mavenName[0] + "-" + mavenName[1] + ".jar");
									if (!dir.exists()) dir.mkdirs();
									List<String> disallowed = new ArrayList<>();
									disallowed.add("osx");
									disallowed.add("windows");
									disallowed.add("linux");
									JsonArray rules = library.getAsJsonArray("rules");
									if (rules == null) {
										JsonObject downloads = library.getAsJsonObject("downloads");
										JsonObject artifact = downloads.getAsJsonObject("artifact");
										JsonObject classifiers = downloads.getAsJsonObject("classifiers");
										if (artifact != null) {
											try {
												if (!jar.exists()) {
													System.out.println(jar.getAbsolutePath() + " indiriliyor...");
													InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
													jar.createNewFile();
													IOUtils.copy(stream, new FileOutputStream(jar));
												} else if (artifact.get("size").getAsLong() != jar.length()) {
													System.out.println(jar.getAbsolutePath() + " indiriliyor...");
													InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
													IOUtils.copy(stream, new FileOutputStream(jar));
												}
											} catch (IOException e1) {
												
												e1.printStackTrace();
											}
										}
										
										if (SystemUtils.IS_OS_WINDOWS) {
											if (classifiers != null && library.getAsJsonObject("natives") != null) {
												if (library.getAsJsonObject("natives").has("windows")) {
													JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("windows").getAsString().replaceAll("\\$\\{arch\\}", arch()));
													try {
														InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
														File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("windows").getAsString().replaceAll("\\$\\{arch\\}", arch())+".jar");
														if (!nativeJar.exists()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															nativeJar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
											}
										} else if (SystemUtils.IS_OS_LINUX) {
											if (classifiers != null && library.getAsJsonObject("natives") != null) {
												if (library.getAsJsonObject("natives").has("linux")) {
													JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("linux").getAsString());
													try {
														InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
														File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("linux").getAsString()+".jar");
														if (!nativeJar.exists()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															nativeJar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
											}
										} else if (SystemUtils.IS_OS_MAC) {
											if (classifiers != null && library.getAsJsonObject("natives") != null) {
												if (library.getAsJsonObject("natives").has("osx")) {
													JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("osx").getAsString());
													try {
														InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
														File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("osx").getAsString()+".jar");
														if (!nativeJar.exists()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															nativeJar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
															System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
															IOUtils.copy(stream, new FileOutputStream(nativeJar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
											}
										}
									} else {
										rules.forEach((action3) -> {
											JsonObject rule = action3.getAsJsonObject();
											if (rule.get("action").getAsString().equalsIgnoreCase("allow")) {
												if (rule.get("os") != null) {
													JsonObject oses = rule.getAsJsonObject("os");
													disallowed.remove(oses.get("name").getAsString());
												} else {
													disallowed.clear();
												}
											} else if (rule.get("action").getAsString().equalsIgnoreCase("disallow")) {
												if (rule.get("os") != null) {
													JsonObject oses = rule.getAsJsonObject("os");
													disallowed.add(oses.get("name").getAsString());
												} else {
													disallowed.add("osx");
													disallowed.add("windows");
													disallowed.add("linux");
												}
											}
										});
										
										if (SystemUtils.IS_OS_WINDOWS) {
											if (!disallowed.contains("windows")) {
												JsonObject downloads = library.getAsJsonObject("downloads");
												JsonObject artifact = downloads.getAsJsonObject("artifact");
												JsonObject classifiers = downloads.getAsJsonObject("classifiers");
												if (artifact != null) {
													try {
														if (!jar.exists()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															jar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(jar));
														} else if (artifact.get("size").getAsLong() != jar.length()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															IOUtils.copy(stream, new FileOutputStream(jar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
												
												if (classifiers != null) {
													if (library.getAsJsonObject("natives").has("windows")) {
														JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("windows").getAsString().replaceAll("\\$\\{arch\\}", arch()));
														try {
															InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
															File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("windows").getAsString().replaceAll("\\$\\{arch\\}", arch())+".jar");
															if (!nativeJar.exists()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																nativeJar.createNewFile();
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															}
														} catch (IOException e1) {
															
															e1.printStackTrace();
														}
													}
												}
											}
										} else if (SystemUtils.IS_OS_LINUX) {
											if (!disallowed.contains("linux")) {
												JsonObject downloads = library.getAsJsonObject("downloads");
												JsonObject artifact = downloads.getAsJsonObject("artifact");
												JsonObject classifiers = downloads.getAsJsonObject("classifiers");
												if (artifact != null) {
													try {
														if (!jar.exists()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															jar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(jar));
														} else if (artifact.get("size").getAsLong() != jar.length()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															IOUtils.copy(stream, new FileOutputStream(jar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
												
												if (classifiers != null && library.getAsJsonObject("natives") != null) {
													if (library.getAsJsonObject("natives").has("linux")) {
														JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("linux").getAsString());
														try {
															InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
															File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("linux").getAsString()+".jar");
															if (!nativeJar.exists()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																nativeJar.createNewFile();
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															}
														} catch (IOException e1) {
															
															e1.printStackTrace();
														}
													}
												}
											}
										} else if (SystemUtils.IS_OS_MAC) {
											if (!disallowed.contains("osx")) {
												JsonObject downloads = library.getAsJsonObject("downloads");
												JsonObject artifact = downloads.getAsJsonObject("artifact");
												JsonObject classifiers = downloads.getAsJsonObject("classifiers");
												if (artifact != null) {
													try {
														if (!jar.exists()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															jar.createNewFile();
															IOUtils.copy(stream, new FileOutputStream(jar));
														} else if (artifact.get("size").getAsLong() != jar.length()) {
															System.out.println(jar.getAbsolutePath() + " indiriliyor...");
															InputStream stream = new URL(artifact.get("url").getAsString()).openStream();
															IOUtils.copy(stream, new FileOutputStream(jar));
														}
													} catch (IOException e1) {
														
														e1.printStackTrace();
													}
												}
												
												if (classifiers != null && library.getAsJsonObject("natives") != null) {
													if (library.getAsJsonObject("natives").has("osx")) {
														JsonObject artifact_native = classifiers.getAsJsonObject(library.getAsJsonObject("natives").get("osx").getAsString());
														try {
															InputStream stream = new URL(artifact_native.get("url").getAsString()).openStream();
															File nativeJar = new File(dir, mavenName[0] + "-" + mavenName[1] + "-"+library.getAsJsonObject("natives").get("osx").getAsString()+".jar");
															if (!nativeJar.exists()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																nativeJar.createNewFile();
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															} else if (artifact_native.get("size").getAsLong() != nativeJar.length()) {
																System.out.println(nativeJar.getAbsolutePath() + " indiriliyor...");
																IOUtils.copy(stream, new FileOutputStream(nativeJar));
															}
														} catch (IOException e1) {
															
															e1.printStackTrace();
														}
													}
												}
											}
										}
									}
								});
							} catch (IOException e1) {
								e1.printStackTrace();
							}
							
						}
					});
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				System.out.println("indirme tamamlandý.");
			}
		}
	}
	
	private static String arch() {
		Map<String, String> archMap = new HashMap<String, String>();
		archMap.put("x86", "32");
		archMap.put("i386", "32");
		archMap.put("i486", "32");
		archMap.put("i586", "32");
		archMap.put("i686", "32");
		archMap.put("x86_64", "64");
		archMap.put("amd64", "64");
		return archMap.get(SystemUtils.OS_ARCH);
	}
	
	static class FrameLog extends JFrame {
		JTextArea area;
		
		public FrameLog() {
			this.setSize(600, 350);
			area = new JTextArea();
			area.setColumns(50);
			area.setRows(1);
			area.setLineWrap(true);
			area.setWrapStyleWord(true);
			area.setEnabled(false);
			area.setDisabledTextColor(Color.WHITE);
			area.setBackground(Color.BLACK);
	        DefaultCaret caret = (DefaultCaret) area.getCaret();
	        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
			this.add(new JScrollPane(area));
			this.setVisible(true);
			this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		
		public void setPrints() {
			System.setErr(new PrintStream(new OutputStream() {
				
				@Override
				public void write(int b) throws IOException {
			        area.append(String.valueOf((char)b));
			        try {
						area.setCaretPosition(area.getLineStartOffset(area.getLineCount() - 1));
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
				}
			}));
			
			System.setOut(new PrintStream(new OutputStream() {
				
				@Override
				public void write(int b) throws IOException {
			        area.append(String.valueOf((char)b));
			        try {
						area.setCaretPosition(area.getLineStartOffset(area.getLineCount() - 1));
					} catch (BadLocationException e) {
						e.printStackTrace();
					}
				}
			}));
		}
	}
	
}
