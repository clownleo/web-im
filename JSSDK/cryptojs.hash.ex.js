/**
 * Created by leo on 16-5-11.
 */
CryptoJS.hash16bytes=function(str){
    if(typeof str != "string"){
        console.error("parameter must be a string");
        return ;
    }
    var hash=CryptoJS.MD5(str);
    hash.words[0]^=hash.words[2];
    hash.words[1]^=hash.words[3];
    hash.words = hash.words.slice(0,2);
    hash.sigBytes=8;
    return hash.toString();
};