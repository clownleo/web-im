var client = clientIO();
var cnt=0;
client.onMsg()
    .filter(msg => msg.from_user == 'kiss')
    .subscribe(function(){
        if(++cnt % 1000 == 0){
            console.log("已收到" + cnt + '条消息');
        }
    });
function batchSend2abc(num) {
    Rx.Observable.range(1, num)
        .flatMap(index => client.sendPrivateMessage({to_user: 'abc', content: '' + index}))
        .subscribe(
            ()=> {},
            err => console.err(err.code),
            () => console.log('finish')
        );
}


//client.login({username: 'abc', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));
//client.login({username: 'kiss', pwd: 'abc'}).subscribe(()=>{},(error)=>console.log("error:"+error.code));