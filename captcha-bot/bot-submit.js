const puppeteer = require("puppeteer");

(async () => {
  const browser = await puppeteer.launch({
    headless: true,
    args: [
      "--no-sandbox",
      "--disable-setuid-sandbox",
      "--disable-blink-features=AutomationControlled"
    ]
  });

  const page = await browser.newPage();

  await page.setUserAgent(
    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
  );
// https://experienceleaguecommunities.adobe.com/adobe-experience-manager-forms-10/how-to-override-default-submit-spinner-in-aem-core-components-adaptive-form-248688?tid=248688&postid=747157#post747157
  await page.goto(
    "http://localhost:4502/content/myformsite/us/en/home.html?wcmmode=disabled",
    { waitUntil: "networkidle0" }
  );

  // Wait for AF runtime
  await page.waitForFunction(() => {
    return window.guideBridge && window.guideBridge.getFormModel;
  }, { timeout: 45000 });

  // Wait for first field
  await page.waitForSelector("#textinput-eb3da4ca01-widget", { timeout: 15000 });

  await page.type("#textinput-eb3da4ca01-widget", "BOT-123");
  await page.type("#textinput-2263f8757f-widget", "Bot User");
  await page.type("#textinput-4f6b7c59ce-widget", "bot@test.com");

  await page.evaluate(() => {
    document.querySelector("button[type='submit']").removeAttribute("disabled");
  });

  await page.click("button[type='submit']");

  await page.waitForTimeout(5000);
  await browser.close();
})();
