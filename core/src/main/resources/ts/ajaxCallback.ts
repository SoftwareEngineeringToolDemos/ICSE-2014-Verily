

module Verily {


    export class AjaxCallback {

        private onSuccess : Function;
        private onFailure : Function;

        constructor(onSuccess:Function, onFailure:Function){
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }

        public failure(d:any){
            this.onFailure(d);
        }

        public success(d:any){
            this.onSuccess(d);
        }



    }
}