package com.pageobject.controller;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.Augmenter;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * WebDriver implementation of BrowserController.
 * 
 * @author michal.nahlik
 *
 */
public class WebDriverBrowserImpl implements BrowserController {

	@Value("${browser.implicit.timeout:5000}")
	private long implicitTimeout;
	
	@Value("${browser.script.timeout:15000}")
	private long scriptTimeout; 
	
	@Value("${browser.timeout:30000}")
	private long timeout;
	
	private long waitStep = 100;
	
	private WebDriver driver;
	protected Logger logger = LoggerFactory.getLogger(getClass());
	
	@Autowired
	public void setDriver(WebDriver driver) {
		this.driver = driver;
		
		//basic setup for the webdriver
		setTimeout(timeout);
		logger.info("Setting up the implicit timeout to " + implicitTimeout
				+ " ms. Can be specified by property browser.implicit.timeout");
		driver.manage().timeouts().implicitlyWait(implicitTimeout, TimeUnit.MILLISECONDS);
		logger.info("Setting up the script timeout to " + scriptTimeout
				+ " ms. Can be specified by property browser.script.timeout");
		driver.manage().timeouts().setScriptTimeout(scriptTimeout, TimeUnit.MILLISECONDS);
	}
	
	public WebDriver getDriver() {
		return driver;
	}
	

	public void setTimeout(long timeout) {
		this.timeout = timeout;
		logger.info("Setting up the page load timeout to " +  timeout + " ms");
		driver.manage().timeouts().pageLoadTimeout(timeout, TimeUnit.MILLISECONDS);
	}
	
	public long getTimeout() {
		return timeout;
	}
	
	/**
	 * Private method to obtain the locator used by Web Driver to locate web
	 * elements.
	 * 
	 * @param locator
	 *            String representation of the locator.
	 *            
	 * @return By locator - locator type used by WebDriver that was created
	 *         based on the string representation.
	 */
	private By locatorPreprocessor(String locator) {
		By byLocator = null;
		String identifier;
		int typeIndex = locator.indexOf("=");
		
		identifier = locator.substring(typeIndex + 1);
		
		if(locator.startsWith("id=") || typeIndex == -1) {
			byLocator = By.id(identifier);
		} else if (locator.startsWith("name=")) {
			byLocator = By.name(identifier);
		} else if (locator.startsWith("css=")) {
			byLocator = By.cssSelector(identifier);
		} else if (locator.startsWith("xpath=")) {
			byLocator = By.xpath(identifier);
		} else if (locator.startsWith("text=")) {
			byLocator = By.linkText(identifier);
		} else {
			logger.error("Locator type was not recognized. Locator type: "
					+ locator.substring(0, typeIndex) + ". Identifier: "
					+ identifier + ".");
		}
		
		return byLocator;
	}

	public void open(String url) {
		driver.get(url);
	}
	
	public void type(String locator, String value) {
		By byLocator = locatorPreprocessor(locator);
		
		WebElement element = driver.findElement(byLocator); 
		element.sendKeys(value);
	}
	
	public void clear(String locator) {
		By byLocator = locatorPreprocessor(locator);
		
		WebElement element = driver.findElement(byLocator);
		element.clear();
	}

	public void click(String locator) {
		By byLocator = locatorPreprocessor(locator);
		driver.findElement(byLocator).click();
	}
		
	public void select(String locator, String option) {
		By byLocator = locatorPreprocessor(locator);
		WebElement element = driver.findElement(byLocator);
		Select select = new Select(element);
		
		int typeIndex = option.indexOf("=");
		String val = option.substring(typeIndex + 1);
		
		if(option.startsWith("value=") || typeIndex == -1) {
			select.selectByValue(val);
		} else if (option.startsWith("label=")) {
			select.selectByVisibleText(val);
		} else if (option.startsWith("index=")) {
			select.selectByIndex(Integer.valueOf(val));
		} else {
			logger.warn("Value locator was not recognized. Value locator type: "
					+ option.substring(0, typeIndex)
					+ " Value identifier: "
					+ option.substring(typeIndex));
		}
		
	}

	public void waitForPageToLoad(long timeout) {
		long waitingFor = 0;

		while(!isPageLoaded()) {
			waitFor(waitStep);
			waitingFor += waitStep;
			if(waitingFor >= timeout) {
				logger.error("The page wasn't loaded yet. Time waited: " + waitingFor);
			}
		}
	}

	public void waitForElementPresent(String locator, long timeout) {
		long waitingFor = 0;
				
		logger.info("Waiting for element " + locator + " present.");
		
		while(!isElementPresent(locator) && waitingFor < timeout) {
			waitFor(waitStep);
			waitingFor += waitStep;
		}

	}
	
	public void waitFor(long time) {
		try {
			Thread.sleep(time);
		} catch (InterruptedException ie) {
			logger.error("Interrupted exception: " + ie.getMessage());
			ie.printStackTrace();
		}
	}
	
	public void waitUntil(String script, long timeout) {
		long waitingFor = 0;
		
		logger.info("Waiting until the script: " + script + " returns true.");
		
		while(!(executeScript("return " + script).toString()).equalsIgnoreCase("true") && waitingFor < timeout) {
			waitFor(waitStep);
			waitingFor += waitStep;
		}
	}
	
	public boolean isTextPresent(String text) {
		return getText("css=BODY").contains(text);
	}

	public boolean isElementEnabled(String locator) {
		By byLocator = locatorPreprocessor(locator);
		WebElement element = driver.findElement(byLocator);
		return element.isEnabled();
	}

	public boolean isElementPresent(String locator) {
		By byLocator; 
		List<WebElement> element;
		
		try {
			byLocator = locatorPreprocessor(locator);
			element = driver.findElements(byLocator);
		} catch (NoSuchElementException nsee) {
			return false;
		}

   		if(element != null && element.size() > 0) {
   			return true;
   		} else {
   			return false;
   		}
	}

	public void closePage() {
		driver.close();
	}

	public boolean isWindowOpened(String windowIdentifier) {
		int typeIndex = windowIdentifier.indexOf("=");
		String identifier = windowIdentifier.substring(typeIndex + 1);
		String currentWindow = null;
		
		try {
			currentWindow = driver.getWindowHandle();
		} catch (NoSuchWindowException nswe) {
			logger.info("No current window selected.");
		}
		
		Set<String> windowHandles = driver.getWindowHandles();
		Iterator<String> iterator = windowHandles.iterator();
		
		logger.info("Currently opened windows: " + windowHandles.size());
		
		while (iterator.hasNext()) {
			
			driver.switchTo().window(iterator.next());
			
			if(windowIdentifier.startsWith("title=") || typeIndex == -1) {
				if(driver.getTitle().contains(identifier)) {
					return true;
				}
			} else if (windowIdentifier.startsWith("name=")) {
				String windowName = executeScript("return document.name").toString();
			    if(windowName.contains(identifier)) {
			    	return true;
			    }
			} else {
				logger.warn("The window identifier type was not recognized. Trying to use the defualt method");
				if(driver.getWindowHandle().equals(identifier)) {
					return true;
				}
			}
		}
		
		if(currentWindow != null) {
			driver.switchTo().window(currentWindow);
		}
		
		return false;
	}
	
	public void waitUntilWindowIsPresent(String windowIdentifier) {
		long waitedFor = 0;
		
		logger.info("Waiting for a window " + windowIdentifier + " to be present.");
		
		while(!isWindowOpened(windowIdentifier) && waitedFor < getTimeout()) {
			waitedFor += waitStep;
			waitFor(waitStep);
			
		}
		
		logger.info("Waited for a window " + windowIdentifier + " for " + waitedFor + " milliseconds.");
	}
	
	public void selectWindow(String windowIdentifier) {
		int typeIndex = windowIdentifier.indexOf("=");
		String identifier = windowIdentifier.substring(typeIndex + 1);
		
		if(windowIdentifier.startsWith("title=") || typeIndex == -1) {
			selectWindowByTitle(identifier);
		} else if (windowIdentifier.startsWith("name=")) {
			selectWindowByName(identifier);
		} else {
			logger.warn("The window identifier type was not recognized. Trying to use the defualt method");
			driver.switchTo().window(windowIdentifier);
		}

	}
	
	public void selectWindowByTitle(String title) {
		for(String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			if(driver.getTitle().contains(title)) break;
		}
	}
	
	public void selectWindowByName(String windowName) {
		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
		    String name = executeScript("return document.name").toString();
		    if(name.contains(windowName)) break;
		}
	}
	
	public void maximizeWindow() {
		driver.manage().window().maximize();
	}

	public void captureScreenshot(String path) throws IOException {
		WebDriver screenshotDriver = new Augmenter().augment(driver);
		FileOutputStream out = new FileOutputStream(path);
		byte[] screenshot = ((TakesScreenshot) screenshotDriver).getScreenshotAs(OutputType.BYTES);
		out.write(screenshot);
		out.close();
	}

	public int getElementCount(String locator) {
		By byLocator = locatorPreprocessor(locator);
		return driver.findElements(byLocator).size();
	}

	public String getElementValue(String locator) {
		return getElementAttribute(locator, "value");
	}
	
	public String getElementAttribute(String locator, String attributeName) {
		By byLocator = locatorPreprocessor(locator);
		
		return driver.findElement(byLocator).getAttribute(attributeName);
	}
	
	public String getSelectedValue(String locator) {
		By byLocator = locatorPreprocessor(locator);
		Select select = new Select(driver.findElement(byLocator));
		return select.getFirstSelectedOption().getAttribute("value");
	}
	
	public String getSelectedLabel(String locator) {
		By byLocator = locatorPreprocessor(locator);
		Select select = new Select(driver.findElement(byLocator));
		return select.getFirstSelectedOption().getText();
	}
	
	public String[] getSelectedValues(String locator) {
		By byLocator = locatorPreprocessor(locator);
		Select select = new Select(driver.findElement(byLocator));
		List<WebElement> allSelectedOptions = select.getAllSelectedOptions();
		List<String> allSelectedValues = new ArrayList<String> (allSelectedOptions.size());
		
		for (WebElement option : allSelectedOptions) {
			allSelectedValues.add(option.getAttribute("value"));
		}
		
		return allSelectedValues.toArray(new String [allSelectedValues.size()]);
	}
	
	public String[] getSelectedLabels(String locator) {
		By byLocator = locatorPreprocessor(locator);
		Select select = new Select(driver.findElement(byLocator));
		List<WebElement> allSelectedOptions = select.getAllSelectedOptions();
		List<String> allSelectedLabels = new ArrayList<String> (allSelectedOptions.size());
		
		for (WebElement option : allSelectedOptions) {
			allSelectedLabels.add(option.getText());
		}
		
		return allSelectedLabels.toArray(new String[allSelectedLabels.size()]);
	}

	public String getText(String locator) {
		By byLocator = locatorPreprocessor(locator);
		return driver.findElement(byLocator).getText();
	}

	public String getTitle() {
		return driver.getTitle();
	}
	
	public String getPageState() {
		return executeScript("return document.readyState").toString();
	}
	
	public boolean isPageLoaded() {
		if (getPageState().equalsIgnoreCase("complete")) return true;
		return false;
	}

	public String[] getAllWindowNames() {
		String current = driver.getWindowHandle();

	    List<String> windowNames = new ArrayList<String>();
	    for (String handle : driver.getWindowHandles()) {
	      driver.switchTo().window(handle);
	      windowNames.add(executeScript("return window.document.name").toString());
	    }

	    driver.switchTo().window(current);

	    return windowNames.toArray(new String[windowNames.size()]);
	}

	public String[] getAllWindowTitles() {
		String current = driver.getWindowHandle();

	    List<String> windowTitles = new ArrayList<String>();
	    for (String handle : driver.getWindowHandles()) {
	      driver.switchTo().window(handle);
	      windowTitles.add(driver.getTitle());
	    }

	    driver.switchTo().window(current);

	    return windowTitles.toArray(new String[windowTitles.size()]);
	}

	public void closeAllBut(String windowIdentifier) {
		String identifierType = windowIdentifier.substring(0, windowIdentifier.indexOf("="));
		String identifier = windowIdentifier.substring(windowIdentifier.indexOf("=") + 1);
		for (String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle.toString());
			
			String result = executeScript("return window.document." + identifierType).toString();
			if(!result.contains(identifier)) {
				closePage();
			}
		}
		selectWindow(windowIdentifier);
	}

	public void openAndSelectWindow(String url) {
		Set<String> windowsBefore = driver.getWindowHandles();
		open(url);
		Set<String> windowsAfter = driver.getWindowHandles();
		windowsAfter.removeAll(windowsBefore);
		
		driver.switchTo().window(windowsAfter.iterator().next());
		
	}
	
	public Object executeScript(String script) {
		return ((JavascriptExecutor) driver).executeScript(script);
	}
	
	public String getCurrentUrl() {
		return driver.getCurrentUrl();
	}

	public String getPageSource() {
		return driver.getPageSource();
	}
	
	public void refresh(){
		driver.navigate().refresh();
	}
	
	public void deleteAllCookies() {
		driver.manage().deleteAllCookies();
	}
	
}
