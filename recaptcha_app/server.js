const express = require("express");
const axios = require("axios");
const bodyParser = require("body-parser");

const app = express();
app.use(bodyParser.json());
app.use(express.static("."));

const SECRET_KEY = "6LfAYWQsAAAAAINXagiqf7QtPH7AngZKwWnZDwZz";

app.post("/verify", async (req,res)=>{
  const {token} = req.body;
  try{
    const g = await axios.post("https://www.google.com/recaptcha/api/siteverify",null,{
      params:{secret:SECRET_KEY,response:token}
    });
    const {success,score,action} = g.data;
    if(success && score>=0.5 && action==="submit"){
      res.json({success:true,score});
    } else {
      res.json({success:false,score});
    }
  }catch(e){
    res.json({success:false});
  }
});

app.listen(3000,()=>console.log("Running on http://localhost:3000"));
