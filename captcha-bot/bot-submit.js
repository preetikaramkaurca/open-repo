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

  // VERY bot-like user agent
  await page.setUserAgent(
    "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
  );

  await page.goto("http://localhost:4502/content/forms/af/your-form.html", {
    waitUntil: "networkidle2"
  });

  // No mouse, no scroll, no delays = bot behavior
  await page.type("input[name='firstName']", "BotTest");
  await page.type("input[name='email']", "bot@test.com");

  await page.click("button[type='submit']");

  await page.waitForTimeout(5000);
  await browser.close();
})();
