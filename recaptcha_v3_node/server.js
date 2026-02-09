const express = require("express");
const axios = require("axios");
const app = express();

app.use(express.urlencoded({ extended: true }));
app.use(express.json());
app.use(express.static("public"));

const SITE_KEY = "Y6Lf-pGQsAAAAAI7eGYM_AiVEJL1QYz9QuQSDhJlI";
const PROJECT_ID = "aem-af-recaptcha";
const API_KEY = "6Lf-pGQsAAAAADpXTFqIr2Wn_ldCxsW4MfetguWj";

app.post("/verify", async (req, res) => {
  try {
    const token = req.body.token;

    const response = await axios.post(
      `https://recaptchaenterprise.googleapis.com/v1/projects/${PROJECT_ID}/assessments?key=${API_KEY}`,
      {
        event: { token: token, siteKey: SITE_KEY }
      }
    );

    const score = response.data.riskAnalysis.score;
    console.log("score---->"+score);
    res.send("Score: " + score);
  } catch (e) {
    res.status(500).send(e.toString());
  }
});

app.listen(3000, () => console.log("Server running on http://localhost:3000"));
