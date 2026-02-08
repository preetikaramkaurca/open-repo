
const express = require("express");
const bodyParser = require("body-parser");
const path = require("path");
const { RecaptchaEnterpriseServiceClient } = require("@google-cloud/recaptcha-enterprise");

const app = express();
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, "public")));

const client = new RecaptchaEnterpriseServiceClient();

const PROJECT_ID = "YOUR_PROJECT_ID";
const SITE_KEY = "YOUR_SITE_KEY";

app.post("/api/submit", async (req, res) => {
  const { name, email, token } = req.body;

  try {
    const [response] = await client.createAssessment({
      parent: `projects/${PROJECT_ID}`,
      assessment: {
        event: {
          token: token,
          siteKey: SITE_KEY,
          expectedAction: "form_submit",
        },
      },
    });

    if (!response.tokenProperties.valid) {
      return res.status(400).json({ success: false, message: "Invalid token" });
    }

    const score = response.riskAnalysis.score;
    console.log("Score:", score, response.riskAnalysis.reasons);

    if (score < 0.5) {
      return res.status(403).json({ success: false, message: "Bot detected" });
    }

    console.log("Form Data:", { name, email });
    res.json({ success: true, message: "Form submitted successfully!" });

  } catch (err) {
    console.error(err);
    res.status(500).json({ success: false, message: "Verification failed" });
  }
});

app.listen(3000, () => console.log("Server running at http://localhost:3000"));
